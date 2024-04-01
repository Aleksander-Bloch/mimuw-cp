package solutions04;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.IntBinaryOperator;


public class MatrixRowSumsConcurrent {

    private static final int NUM_ROWS = 10;
    private static final int NUM_COLUMNS = 100;

    private static class Matrix {

        private final int numRows;
        private final int numColumns;
        private final IntBinaryOperator definition;

        public Matrix(int numRows, int numColumns, IntBinaryOperator definition) {
            this.numRows = numRows;
            this.numColumns = numColumns;
            this.definition = definition;
        }

        public int[] rowSums() throws InterruptedException {
            int[] rowSums = new int[numRows];
            List<Thread> threads = new ArrayList<>();
            ConcurrentHashMap<Integer, LinkedBlockingQueue<Integer>> rowAcc = new ConcurrentHashMap<>();
            for (int columnNo = 0; columnNo < numColumns; ++columnNo) {
                Thread t = new Thread(new PerColumnDefinitionApplier(columnNo, rowAcc, Thread.currentThread()));
                threads.add(t);
            }
            for (Thread t : threads) {
                t.start();
            }
            for (int rowNo = 0; rowNo < numRows; rowNo++) {
                LinkedBlockingQueue<Integer> queue = rowAcc.computeIfAbsent(rowNo,
                        f -> new LinkedBlockingQueue<>());
                int requiredQueueSize = numColumns;
                while (requiredQueueSize > 0) {
                    rowSums[rowNo] += queue.take();
                    requiredQueueSize--;
                }
                rowAcc.remove(rowNo);
            }
            for (Thread t : threads) {
                t.join();
            }
            return rowSums;
        }

        private class PerColumnDefinitionApplier implements Runnable {

            private final int myColumnNo;
            private final ConcurrentHashMap<Integer, LinkedBlockingQueue<Integer>> rowAcc;
            private final Thread mainThread;

            private PerColumnDefinitionApplier(
                    int myColumnNo,
                    ConcurrentHashMap<Integer, LinkedBlockingQueue<Integer>> rowAcc,
                    Thread mainThread
            ) {
                this.myColumnNo = myColumnNo;
                this.rowAcc = rowAcc;
                this.mainThread = mainThread;
            }

            @Override
            public void run() {
                try {
                    for (int rowNo = 0; rowNo < numRows; rowNo++) {
                        LinkedBlockingQueue<Integer> queue = rowAcc.computeIfAbsent(rowNo,
                                f -> new LinkedBlockingQueue<>());
                        queue.put(definition.applyAsInt(rowNo, myColumnNo));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    mainThread.interrupt();
                    System.err.println("Wątek " + myColumnNo + " przerwany");
                }
            }

        }
    }


    public static void main(String[] args) {
        Matrix matrix = new Matrix(NUM_ROWS, NUM_COLUMNS, (row, column) -> {
            int a = 2 * column + 1;
            return (row + 1) * (a % 4 - 2) * a;
        });
        try {

            int[] rowSums = matrix.rowSums();

            for (int i = 0; i < rowSums.length; i++) {
                System.out.println(i + " -> " + rowSums[i]);
            }

        } catch (InterruptedException e) {
            System.err.println("Obliczenie przerwane");
            return;
        }
    }

}
