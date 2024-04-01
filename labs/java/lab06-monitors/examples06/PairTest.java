package examples06;

public class PairTest {

    private static final int NUM_SWAPS = 1000000;
    private static final int NUM_CHECKS = 1000000;

    public static void main(String[] args) {
        Pair<Integer> pair = new Pair<>(1, 2);
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < NUM_SWAPS; ++i) {
                    pair.swap();
                }
            }
        }).start();
        int numEqual = 0;
        for (int i = 0; i < NUM_CHECKS; ++i) {
            if (pair.areEqual()) {
                ++numEqual;
            }
        }
        System.out.println("Równe: " + numEqual);
    }

}
