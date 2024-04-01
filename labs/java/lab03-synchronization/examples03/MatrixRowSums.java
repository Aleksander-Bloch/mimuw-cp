package examples03;

import java.util.function.IntBinaryOperator;

public class MatrixRowSums {

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

        public int[] rowSums() {
            int[] rowSums = new int[numRows];
            for (int rowNo = 0; rowNo < numRows; ++rowNo) {
                int sum = 0;
                for (int columnNo = 0; columnNo < numColumns; ++columnNo) {
                    sum += definition.applyAsInt(rowNo, columnNo);
                }
                rowSums[rowNo] = sum;
            }
            return rowSums;
        }
    }

    public static void main(String[] args) {
        Matrix matrix = new Matrix(NUM_ROWS, NUM_COLUMNS, (row, column) -> {
            int a = 2 * column + 1;
            return (row + 1) * (a % 4 - 2) * a;
        });

        int[] rowSums = matrix.rowSums();

        for (int i = 0; i < rowSums.length; i++) {
            System.out.println(i + " -> " + rowSums[i]);
        }
    }

}
