package examples06;

import java.util.ArrayList;
import java.util.List;

public class SwapperTest {

    private static final int NUM_CYCLES = 3;
    private static final int NUM_THREADS = 10;
    private static final int STEP = 7;

    private static final Swapper<Integer> swapper = new Swapper<>(0);

    private static class Worker implements Runnable {

        private final int myNo;
        private final int nextNo;
        private final Thread mainThread;

        private Worker(int myNo, int nextNo, Thread mainThread) {
            this.myNo = myNo;
            this.nextNo = nextNo;
            this.mainThread = mainThread;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < NUM_CYCLES; ++i) {
                    swapper.ifEqualThenSet(myNo, nextNo);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Pracownik " + myNo + " przerwany");
                mainThread.interrupt();
            }
        }

    }

    public static void main(String[] args) {
        Thread mainThread = Thread.currentThread();
        List<Thread> workerThreads = new ArrayList<>();
        for (int i = 0; i < NUM_THREADS; ++i) {
            Thread t = new Thread(new Worker(i, (i + STEP) % NUM_THREADS, mainThread));
            workerThreads.add(t);
            t.start();
        }
        try {
            for (Thread t : workerThreads) {
                t.join();
            }
            for (int n : swapper.getHistory()) {
                System.out.print(" " + n);
            }
            System.out.println();
        } catch (InterruptedException e) {
            mainThread.interrupt();
            for (Thread t : workerThreads) {
                t.interrupt();
            }
            System.err.println("Główny przerwany");
        }
    }

}
