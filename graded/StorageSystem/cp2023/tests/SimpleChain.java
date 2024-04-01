package cp2023.tests;

import java.util.List;
import java.util.Map;

public class SimpleChain {
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
            new TransferParams(1, 1, 2, 10, 10, 10),
            new TransferParams(2, 2, 3, 10, 1000, 10),
            new TransferParams(3, 3, 0, 1000, 10, 10)
        );

        var testRunner = new TestRunner(deviceTotalSlots, componentPlacement, paramsList);
        testRunner.run();
    }
}
