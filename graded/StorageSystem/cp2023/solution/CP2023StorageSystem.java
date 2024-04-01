package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;
import cp2023.exceptions.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;

public class CP2023StorageSystem implements StorageSystem {
    private final HashMap<DeviceId, Integer> numOfPlacesLeftOnDevice;
    private final ConcurrentHashMap<ComponentId, Optional<DeviceId>> componentPlacement;
    private final ConcurrentHashMap<ComponentId, Boolean> transferredComponents; // used when checking if the component is operated on
    private final ConcurrentHashMap<ComponentId, Semaphore> readyToPrepareTransferSync; // wait when transfer is not permitted
    private final ConcurrentHashMap<ComponentId, Semaphore> readyToPerformTransferSync; // wait for releasing component to finish prepare()
    private final ConcurrentHashMap<DeviceId, ConcurrentLinkedDeque<ComponentId>> componentsLeavingFreePlaceOnDevice;
    private final HashMap<DeviceId, Deque<ComponentId>> waitingQueuesForDevice; // main structure for storing transfer graph
    private final HashMap<ComponentId, ComponentId> whoWantsMyPlace; // used for reserving places when breaking chain/cycle
    private final Semaphore readyToDetectAllowedTransfers;
    private ComponentId latestReleasingComponent; // remember who released us
    private boolean releasingCycledTransfers; // remember whether we are releasing chained or cycled transfers

    public CP2023StorageSystem(HashMap<DeviceId, Integer> numOfPlacesLeftOnDevice,
                               ConcurrentHashMap<ComponentId, Optional<DeviceId>> componentPlacement) {
        this.numOfPlacesLeftOnDevice = numOfPlacesLeftOnDevice;
        this.componentPlacement = componentPlacement;
        this.transferredComponents = new ConcurrentHashMap<>();
        this.readyToPrepareTransferSync = new ConcurrentHashMap<>();
        this.readyToPerformTransferSync = new ConcurrentHashMap<>();
        this.componentsLeavingFreePlaceOnDevice = new ConcurrentHashMap<>();
        this.waitingQueuesForDevice = new HashMap<>();
        this.whoWantsMyPlace = new HashMap<>();
        this.readyToDetectAllowedTransfers = new Semaphore(1);
    }

    @Override
    public void execute(ComponentTransfer transfer) throws TransferException {
        var destinationDeviceId = transfer.getDestinationDeviceId();
        var sourceDeviceId = transfer.getSourceDeviceId();
        var componentId = transfer.getComponentId();
        if (sourceDeviceId == null && destinationDeviceId == null) {
            throw new IllegalTransferType(componentId);
        }
        if (sourceDeviceId != null && !numOfPlacesLeftOnDevice.containsKey(sourceDeviceId)) {
            throw new DeviceDoesNotExist(sourceDeviceId);
        }
        if (destinationDeviceId != null && !numOfPlacesLeftOnDevice.containsKey(destinationDeviceId)) {
            throw new DeviceDoesNotExist(destinationDeviceId);
        }
        var componentCurrentDevice = componentPlacement.get(componentId);
        if (sourceDeviceId == null) {
            if (componentCurrentDevice != null) {
                if (componentCurrentDevice.isEmpty()) {
                    throw new ComponentAlreadyExists(componentId);
                } else {
                    throw new ComponentAlreadyExists(componentId, componentCurrentDevice.get());
                }
            }
        } else {
            if (componentCurrentDevice == null || componentCurrentDevice.isEmpty() ||
                !sourceDeviceId.equals(componentCurrentDevice.get())) {
                throw new ComponentDoesNotExist(componentId, sourceDeviceId);
            }
        }
        if (componentCurrentDevice != null && componentCurrentDevice.get().equals(destinationDeviceId)) {
            throw new ComponentDoesNotNeedTransfer(componentId, destinationDeviceId);
        }
        var previousValue = transferredComponents.put(componentId, true);
        if (previousValue != null) {
            throw new ComponentIsBeingOperatedOn(componentId);
        }
        if (sourceDeviceId == null) {
            componentPlacement.put(componentId, Optional.empty());
        }
        try {
            int numOfPlacesLeftOnTargetDeviceAfterTransfer = 0;
            ComponentId whoFreedMe = null;
            readyToDetectAllowedTransfers.acquire();
            if (destinationDeviceId != null) {
                // number of places can be decreased below zero, then it symbolizes number of components waiting
                numOfPlacesLeftOnTargetDeviceAfterTransfer = numOfPlacesLeftOnDevice.compute(
                    destinationDeviceId, (__, num) -> --num
                );
            }
            // there are no places left on device
            if (numOfPlacesLeftOnTargetDeviceAfterTransfer < 0) {
                if (!findCycledTransfers(destinationDeviceId, sourceDeviceId, componentId, new HashMap<>())) {
                    waitingQueuesForDevice.computeIfAbsent(
                        destinationDeviceId, deviceId -> new LinkedList<>()
                    ).offer(componentId);
                    readyToDetectAllowedTransfers.release();
                    readyToPrepareTransferSync.computeIfAbsent(
                        componentId, __ -> new Semaphore(0)
                    ).acquire();
                    readyToPrepareTransferSync.remove(componentId);
                } else {
                    releasingCycledTransfers = true;
                }
                whoFreedMe = latestReleasingComponent;
            } else {
                releasingCycledTransfers = false;
                findChainedTransfers(componentId);
                if (destinationDeviceId != null) {
                    var leavingQueue = componentsLeavingFreePlaceOnDevice.get(destinationDeviceId);
                    // check if some component is leaving the place we're taking or the place is empty
                    if (leavingQueue != null && numOfPlacesLeftOnTargetDeviceAfterTransfer < leavingQueue.size()) {
                        whoFreedMe = leavingQueue.poll();
                    }
                }
            }
            boolean releasedCycledTransfers = releasingCycledTransfers;
            boolean wasFreed = freeWaitingComponent(componentId, sourceDeviceId);
            transfer.prepare();
            if (sourceDeviceId != null) {
                // decide if we have to leave open semaphore behind for other components to take
                if (releasedCycledTransfers || wasFreed || !componentsLeavingFreePlaceOnDevice.get(sourceDeviceId).remove(componentId)
                ) {
                    readyToPerformTransferSync.computeIfAbsent(
                        componentId, __ -> new Semaphore(0)
                    ).release();
                }
            }
            if (whoFreedMe != null) {
                // someone freed this component, so it will have to wait for prepare() before doing perform()
                readyToPerformTransferSync.computeIfAbsent(
                    whoFreedMe, __ -> new Semaphore(0)
                ).acquire();
                readyToPerformTransferSync.remove(whoFreedMe);
            }
            transfer.perform();
            if (destinationDeviceId == null) {
                componentPlacement.remove(componentId);
            } else {
                componentPlacement.put(componentId, Optional.of(destinationDeviceId));
            }
            transferredComponents.remove(componentId);
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    // Free another component with inheriting critical section.
    private boolean freeWaitingComponent(ComponentId componentId, DeviceId sourceDeviceId) {
        if (sourceDeviceId != null) {
            numOfPlacesLeftOnDevice.compute(sourceDeviceId, (__, num) -> ++num);
        }
        var componentWaitingForMe = whoWantsMyPlace.get(componentId);
        if (componentWaitingForMe == null) {
            if (sourceDeviceId != null && !releasingCycledTransfers) {
                componentsLeavingFreePlaceOnDevice.computeIfAbsent(
                    sourceDeviceId, __ -> new ConcurrentLinkedDeque<>()
                ).offer(componentId);
            }
            readyToDetectAllowedTransfers.release();
            return false;
        } else {
            whoWantsMyPlace.remove(componentId);
            var waitingQueue = waitingQueuesForDevice.get(sourceDeviceId);
            waitingQueue.remove(componentWaitingForMe);
            if (waitingQueue.isEmpty()) {
                waitingQueuesForDevice.remove(sourceDeviceId);
            }
            latestReleasingComponent = componentId;
            readyToPrepareTransferSync.computeIfAbsent(
                componentWaitingForMe, __ -> new Semaphore(0)
            ).release();
            return true;
        }
    }

    private boolean findCycledTransfers(
        DeviceId start, DeviceId currentDevice, ComponentId currentComponent, Map<DeviceId, Boolean> visited
    ) {
        visited.put(currentDevice, true);
        var waitingQueue = waitingQueuesForDevice.get(currentDevice);
        if (waitingQueue == null || waitingQueue.isEmpty()) {
            return false;
        }
        DeviceId sourceDeviceId;
        for (ComponentId waitingComponent : waitingQueue) {
            var optionalSourceDeviceId = componentPlacement.get(waitingComponent);
            if (optionalSourceDeviceId.isEmpty()) {
                continue;
            }
            sourceDeviceId = optionalSourceDeviceId.get();
            if (sourceDeviceId.equals(start)) {
                whoWantsMyPlace.put(currentComponent, waitingComponent);
                latestReleasingComponent = waitingComponent;
                return true;
            }
            boolean isDeviceVisited = visited.getOrDefault(sourceDeviceId, false);
            if (!isDeviceVisited) {
                whoWantsMyPlace.put(currentComponent, waitingComponent);
                if (findCycledTransfers(start, sourceDeviceId, waitingComponent, visited)) {
                    return true;
                }
                whoWantsMyPlace.remove(currentComponent);
            }
        }
        return false;
    }

    private void findChainedTransfers(ComponentId start) {
        Optional<DeviceId> sourceDeviceOptional;
        DeviceId sourceDevice;
        Deque<ComponentId> waitingQueue;
        ComponentId currentComponent = start;
        ComponentId waitingComponent;
        Map<DeviceId, Boolean> visitedDevices = new HashMap<>();
        do {
            sourceDeviceOptional = componentPlacement.get(currentComponent);
            if (sourceDeviceOptional.isEmpty()) {
                return;
            }
            sourceDevice = sourceDeviceOptional.get();
            if (visitedDevices.getOrDefault(sourceDevice, false)) {
                return;
            }
            visitedDevices.put(sourceDevice, true);
            waitingQueue = waitingQueuesForDevice.get(sourceDevice);
            if (waitingQueue == null || waitingQueue.isEmpty()) {
                return;
            }
            waitingComponent = waitingQueue.poll();
            whoWantsMyPlace.put(currentComponent, waitingComponent);
            currentComponent = waitingComponent;
        } while(true);
    }
}
