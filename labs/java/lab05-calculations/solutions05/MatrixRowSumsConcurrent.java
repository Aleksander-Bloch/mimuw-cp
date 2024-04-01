package solutions05;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntBinaryOperator;


public class MatrixRowSumsConcurrent {

    private static final int NUM_ROWS = 10;
    private static final int NUM_COLUMNS = 100;
    private static final int NUM_THREADS = 4;

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
            ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
            try {
                for (int rowNo = 0; rowNo < numRows; rowNo++) {
                    List<Callable<Integer>> calculations = new ArrayList<>();
                    for (int colNo = 0; colNo < numColumns; colNo++) {
                        Callable<Integer> job = new Job(rowNo, colNo, definition);
                        calculations.add(job);
                    }
                    List<Future<Integer>> promises = pool.invokeAll(calculations);
                    for (Future<Integer> next : promises) {
                        rowSums[rowNo] += next.get();
                    }
                }
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } finally {
                pool.shutdown();
            }
            return rowSums;
        }

        
        private class Job implements Callable<Integer> {

            private final int rowNo;
            private final int columnNo;
            private final IntBinaryOperator definition;

            private Job(int rowNo, int columnNo, IntBinaryOperator definition) {
                this.rowNo = rowNo;
                this.columnNo = columnNo;
                this.definition = definition;
            }

            @Override
            public Integer call() {
                return definition.applyAsInt(rowNo, columnNo);
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
        }
    }

}
