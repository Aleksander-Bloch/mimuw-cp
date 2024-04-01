/*
 * University of Warsaw
 * Concurrent Programming Course 2023/2024
 * Java Assignment
 *
 * Author: Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
package cp2023.solution;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;


public final class StorageSystemFactory {

    public static StorageSystem newSystem(Map<DeviceId, Integer> deviceTotalSlots,
                                          Map<ComponentId, DeviceId> componentPlacement) {
        if (deviceTotalSlots == null) {
            throw new IllegalArgumentException("deviceTotalsSlots is null");
        }
        if (componentPlacement == null) {
            throw new IllegalArgumentException("componentPlacement is null");
        }
        if (deviceTotalSlots.isEmpty()) {
            throw new IllegalArgumentException("deviceTotalSlots is empty");
        }
        var numOfPlacesLeftOnDevice = getNumOfPlacesLeftOnDevice(deviceTotalSlots);
        var componentPlacementOptional = getComponentPlacementOptional(componentPlacement, numOfPlacesLeftOnDevice);
        return new CP2023StorageSystem(numOfPlacesLeftOnDevice, componentPlacementOptional);
    }

    private static HashMap<DeviceId, Integer> getNumOfPlacesLeftOnDevice(Map<DeviceId, Integer> deviceTotalSlots) {
        HashMap<DeviceId, Integer> numOfPlacesLeftOnDevice = new HashMap<>(deviceTotalSlots);
        for (var deviceTotalSlotsEntry : numOfPlacesLeftOnDevice.entrySet()) {
            var deviceId = deviceTotalSlotsEntry.getKey();
            var numOfSlotsOnDevice = deviceTotalSlotsEntry.getValue();
            if (numOfSlotsOnDevice == null) {
                throw new IllegalArgumentException("Device " + deviceId + " has undefined number of slots");
            }
            if (numOfSlotsOnDevice <= 0) {
                throw new IllegalArgumentException("Device " + deviceId + " has non-positive number of slots");
            }
        }
        return numOfPlacesLeftOnDevice;
    }

    private static ConcurrentHashMap<ComponentId, Optional<DeviceId>> getComponentPlacementOptional(
            Map<ComponentId, DeviceId> componentPlacement, Map<DeviceId, Integer> numOfPlacesLeftOnDevice
    ) {
        ConcurrentHashMap<ComponentId, Optional<DeviceId>> componentPlacementOptional = new ConcurrentHashMap<>();
        for (var componentPlacementEntry : componentPlacement.entrySet()) {
            var componentId = componentPlacementEntry.getKey();
            var deviceId = getDeviceId(numOfPlacesLeftOnDevice, componentPlacementEntry, componentId);
            componentPlacementOptional.put(componentId, Optional.of(deviceId));
            var placesLeftAfterPlacement = numOfPlacesLeftOnDevice.compute(deviceId, (__, num) -> --num);
            if (placesLeftAfterPlacement < 0) {
                throw new IllegalArgumentException(
                    "Device " + deviceId + " does not have enough capacity to place component " + componentId
                );
            }
        }
        return componentPlacementOptional;
    }

    private static DeviceId getDeviceId(Map<DeviceId, Integer> numOfPlacesLeftOnDevice,
                                        Map.Entry<ComponentId, DeviceId> componentPlacementEntry,
                                        ComponentId componentId) {
        var deviceId = componentPlacementEntry.getValue();
        if (deviceId == null) {
            throw new IllegalArgumentException("Component " + componentId + " placed on undefined device");
        }
        if (!numOfPlacesLeftOnDevice.containsKey(deviceId)) {
            throw new IllegalArgumentException(
                "Component " + componentId + " placed on device " + deviceId +
                " which is not present in deviceTotalSlots map"
            );
        }
        return deviceId;
    }
}
