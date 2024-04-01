package cp2023.tests;

import java.util.List;
import java.util.Map;

public class ThreeComponentCycle {
    public static void main(String[] args) {
        var deviceTotalSlots = Map.of(
                1, 1,
                2, 1,
                3, 1
        );

        var componentPlacement = Map.of(
                1, 1,
                2, 2,
                3, 3
        );

        var paramsList = List.of(
            new TransferParams(1, 1, 2, 20, 1, 1),
            new TransferParams(2, 2, 3, 50, 1, 1),
            new TransferParams(3, 3, 1, 100, 1, 1)
        );

        var testRunner = new TestRunner(deviceTotalSlots, componentPlacement, paramsList);
        testRunner.run();
    }
}
