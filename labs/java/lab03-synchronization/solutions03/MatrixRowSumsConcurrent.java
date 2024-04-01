package solutions03;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
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
            // FIXME: implement
            int[] rowSums = new int[NUM_ROWS];
            int[] row = new int[NUM_COLUMNS];
            CyclicBarrier barrier = new CyclicBarrier(NUM_COLUMNS, new RowSummer(rowSums, row));
            Thread[] threads = new Thread[NUM_COLUMNS];
            for (int i = 0; i < NUM_COLUMNS; i++) {
                Thread t = new Thread(new PerColumnDefinitionApplier(i, barrier, row, Thread.currentThread()));
                t.start();
                threads[i] = t;
            }
            for (Thread t : threads) {
                t.join();
            }

            return rowSums;
        }

        private class PerColumnDefinitionApplier implements Runnable {

            private final int myColumnNo;
            private final CyclicBarrier barrier;
            private final int[] row;
            private final Thread mainThread;

            private PerColumnDefinitionApplier(
                    int myColumnNo,
                    CyclicBarrier barrier,
                    int[] row,
                    Thread mainThread
            ) {
                this.myColumnNo = myColumnNo;
                this.barrier = barrier;
                this.row = row;
                this.mainThread = mainThread;
            }

            @Override
            public void run() {
                // FIXME: implement
                try {
                    for (int i = 0; i < NUM_ROWS; i++) {
                        row[myColumnNo] = definition.applyAsInt(i, myColumnNo);
                        barrier.await();
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    mainThread.interrupt();
                    System.err.println(mainThread.getName() + " interrupted");
                }
            }

        }


        private class RowSummer implements Runnable {

            private final int[] rowSums;
            private final int[] row;
            private int currentRowNo;

            private RowSummer(int[] rowSums, int[] row) {
                this.rowSums = rowSums;
                this.row = row;
                this.currentRowNo = 0;
            }
            
            @Override
            public void run() {
                // FIXME: implement
                for (int i = 0; i < NUM_COLUMNS; i++) {
                    rowSums[currentRowNo] += row[i];
                }
                currentRowNo++;
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
