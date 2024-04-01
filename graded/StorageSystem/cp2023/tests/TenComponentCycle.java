package cp2023.tests;

import java.util.List;
import java.util.Map;

public class TenComponentCycle {
    public static void main(String[] args) {
        var deviceTotalSlots = Map.of(
            1, 1,
            2, 1,
            3, 1,
            4, 1,
            5, 1,
            6, 1,
            7, 1,
            8, 1,
            9, 1,
            10, 1
        );

        var componentPlacement = Map.of(
            1, 1,
            2, 2,
            3, 3,
            4, 4,
            5, 5,
            6, 6,
            7, 7,
            8, 8,
            9, 9,
            10, 10
        );

        var testRunner = getTestRunner(deviceTotalSlots, componentPlacement);
        testRunner.run();
    }

    private static TestRunner getTestRunner(Map<Integer, Integer> deviceTotalSlots, Map<Integer, Integer> componentPlacement) {
        var paramsList = List.of(
            new TransferParams(1, 1, 2, 20, 1, 1),
            new TransferParams(2, 2, 3, 50, 1, 1),
            new TransferParams(3, 3, 4, 100, 1, 1),
            new TransferParams(4, 4, 5, 10, 1, 1),
            new TransferParams(5, 5, 6, 40, 1, 1),
            new TransferParams(6, 6, 7, 60, 1, 1),
            new TransferParams(7, 7, 8, 20, 1, 1),
            new TransferParams(8, 8, 9, 200, 1, 1),
            new TransferParams(9, 9, 10, 10, 1, 1),
            new TransferParams(10, 10, 1, 60, 1, 1)
        );

        return new TestRunner(deviceTotalSlots, componentPlacement, paramsList);
    }
}
