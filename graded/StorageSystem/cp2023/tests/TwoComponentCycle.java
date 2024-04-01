package cp2023.tests;

import java.util.List;
import java.util.Map;

public class TwoComponentCycle {

    public static void main(String[] args) {
        var deviceTotalSlots = Map.of(
            1, 1,
            2, 1
        );

        var componentPlacement = Map.of(
            1, 1,
            2, 2
        );

        var paramsList = List.of(
            new TransferParams(1, 1, 2, 5000, 1, 100),
            new TransferParams(2, 2, 1, 50, 50, 200)
        );

        var testRunner = new TestRunner(deviceTotalSlots, componentPlacement, paramsList);
        testRunner.run();
    }
}
