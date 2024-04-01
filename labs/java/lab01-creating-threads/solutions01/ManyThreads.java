package solutions01;

import java.util.concurrent.ThreadLocalRandom;

public class ManyThreads {

    private static final int THREAD_COUNT = 10;

    private static class Helper implements Runnable {

        private final int n;

        public Helper(int n) {
            this.n = n;
        }

        @Override
        public void run() {
            // FIXME: implement here
            if (n < THREAD_COUNT) {
                Runnable r = new Helper(n + 1);
                Thread t = new Thread(r, "Pomocniczy");
                t.start();
            }
            try {
                Thread.sleep((long) ThreadLocalRandom.current().nextDouble(1.0));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println(n);
        }

    }

    public static void main(String[] args) {
        // FIXME: implement here
        Runnable r = new Helper(1);
        Thread t = new Thread(r, "Pomocniczy");
        t.start();
    }

}
