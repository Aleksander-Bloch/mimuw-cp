package cp2023.tests;

import java.util.*;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;
import cp2023.exceptions.TransferException;
import cp2023.solution.StorageSystemFactory;


public final class TestRunner {
    private final Map<DeviceId, Integer> deviceTotalSlots;
    private final Map<ComponentId, DeviceId> componentPlacement;
    private final List<TransferParams> paramsList;

    public TestRunner(Map<Integer, Integer> deviceTotalSlots, Map<Integer, Integer> componentPlacement, List<TransferParams> paramsList) {
        this.deviceTotalSlots = new HashMap<>();
        for (var entry : deviceTotalSlots.entrySet()) {
            this.deviceTotalSlots.put(new DeviceId(entry.getKey()), entry.getValue());
        }
        this.componentPlacement = new HashMap<>();
        for (var entry : componentPlacement.entrySet()) {
            this.componentPlacement.put(new ComponentId(entry.getKey()), new DeviceId(entry.getValue()));
        }
        this.paramsList = paramsList;
    }

    public void run() {
        var system = StorageSystemFactory.newSystem(deviceTotalSlots, componentPlacement);
        Collection<Thread> users = setupTransferrers(system, paramsList);
        runTransferrers(users);
    }

    private Collection<Thread> setupTransferrers(StorageSystem system, List<TransferParams> paramsList) {
        ArrayList<Thread> transferrers = new ArrayList<>();
        for (var params : paramsList) {
            transferrers.add(new Thread(() -> {
                sleep(params.waitBeforeTransferStartDuration());
                System.out.println("Transferrer " + Thread.currentThread().getId() + " has started.");
                executeTransfer(system, params.componentId(), params.sourceDeviceId(), params.destinationDeviceId(), params.waitBeforePrepareDuration(), params.transferPerformDuration());
                System.out.println("Transferrer " + Thread.currentThread().getId() + " has finished.");
            }));
        }
        return transferrers;
    }

    private void runTransferrers(Collection<Thread> users) {
        for (Thread t : users) {
            t.start();
        }
        for (Thread t : users) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException("panic: unexpected thread interruption", e);
            }
        }
    }


    private void executeTransfer(
            StorageSystem system,
            int componentId,
            int sourceDeviceId,
            int destinationDeviceId,
            long waitBeforePrepareDuration,
            long transferPerformDuration
    ) {
        CompTransfImpl transfer =
            new CompTransfImpl(
                new ComponentId(componentId),
                sourceDeviceId > 0 ? new DeviceId(sourceDeviceId) : null,
                destinationDeviceId > 0 ? new DeviceId(destinationDeviceId) : null,
                waitBeforePrepareDuration,
                transferPerformDuration
            );
        try {
            system.execute(transfer);
        } catch (TransferException e) {
            throw new RuntimeException("Unexpected transfer exception: " + e, e);
        }
    }

    private static void sleep(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption", e);
        }
    }

    private final static class CompTransfImpl implements ComponentTransfer {
        private static int uidGenerator = 0;
        private final int uid;
        private final long owningThread;
        private final Integer phantomSynchronizer;
        private final ComponentId compId;
        private final DeviceId srcDevId;
        private final DeviceId dstDevId;
        private final long waitBeforePrepareDuration;
        private final long transferPerformDuration;
        private boolean prepared;
        private boolean started;
        private boolean done;

        private static synchronized int generateUID() {
            return ++uidGenerator;
        }

        public CompTransfImpl(
                ComponentId compId,
                DeviceId srcDevId,
                DeviceId dstDevId,
                long waitBeforePrepareDuration,
                long transferPerformDuration
        ) {
            this.uid = generateUID();
            this.phantomSynchronizer = 19;
            this.owningThread = Thread.currentThread().getId();
            this.compId = compId;
            this.srcDevId = srcDevId;
            this.dstDevId = dstDevId;
            this.waitBeforePrepareDuration = waitBeforePrepareDuration;
            this.transferPerformDuration = transferPerformDuration;
            this.prepared = false;
            this.started = false;
            this.done = false;
            System.out.println("Transferrer " + this.owningThread +
                    " is about to issue transfer " + this.uid +
                    " of " + this.compId + " from " + this.srcDevId +
                    " to " + this.dstDevId + ".");
        }

        @Override
        public ComponentId getComponentId() {
            return this.compId;
        }

        @Override
        public DeviceId getSourceDeviceId() {
            return this.srcDevId;
        }

        @Override
        public DeviceId getDestinationDeviceId() {
            return this.dstDevId;
        }

        @Override
        public void prepare() {
            synchronized (this.phantomSynchronizer) {
                if (this.prepared) {
                    throw new RuntimeException(
                            "Transfer " + this.uid + " is being prepared more than once!");
                }
                if (this.owningThread != Thread.currentThread().getId()) {
                    throw new RuntimeException(
                            "Transfer " + this.uid +
                                    " is being prepared by a different thread that scheduled it!");
                }
                this.prepared = true;
            }
            sleep(waitBeforePrepareDuration);
            System.out.println("Transfer " + this.uid + " of " + this.compId +
                    " from " + this.srcDevId + " to " + this.dstDevId +
                    " has been prepared by user " + Thread.currentThread().getId() + ".");
        }

        @Override
        public void perform() {
            synchronized (this.phantomSynchronizer) {
                if (! this.prepared) {
                    throw new RuntimeException(
                            "Transfer " + this.uid + " has not been prepared " +
                                    "before being performed!");
                }
                if (this.started) {
                    throw new RuntimeException(
                            "Transfer " + this.uid + " is being started more than once!");
                }
                if (this.owningThread != Thread.currentThread().getId()) {
                    throw new RuntimeException(
                            "Transfer " + this.uid +
                                    " is being performed by a different thread that scheduled it!");
                }
                this.started = true;
            }
            System.out.println("Transfer " + this.uid + " of " + this.compId +
                    " from " + this.srcDevId + " to " + this.dstDevId + " has been started.");
            sleep(transferPerformDuration);
            synchronized (this.phantomSynchronizer) {
                this.done = true;
            }
            System.out.println("Transfer " + this.uid + " of " + this.compId +
                    " from " + this.srcDevId + " to " + this.dstDevId + " has been completed.");
        }

    }

}
