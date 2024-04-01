package solutions02;

import java.util.Arrays;
import java.util.Random;

public class Vector {
    /* VECTOR DEFINITION */
    private final int[] data;
    private static final int SEG_LENGTH = 10;
    private static final Random RANDOM = new Random();

    public Vector(int[] values) {
        data = values;
    }

    public Vector(int setLength) {
        data = new int[setLength];
    }

    private static Vector randomVector(int length) {
        int[] a = new int[length];
        for (int i = 0; i < length; i++) {
            a[i] = RANDOM.nextInt(10);
        }
        return new Vector(a);
    }

    public int getLength() {
        return data.length;
    }

    public int getDataAt(int index) {
        return data[index];
    }

    public void setDataAt(int index, int value) {
        data[index] = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Vector vector = (Vector) o;

        return Arrays.equals(data, vector.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "Vector{" +
                "data=" + Arrays.toString(data) +
                '}';
    }

    /* VECTOR SUM */
    private static class SumHelper implements Runnable {
        private final int start;
        private final int end;
        private final Vector vector;
        private final Vector other;
        private final Vector result;

        public SumHelper(int start, int end, Vector vector, Vector other, Vector result) {
            this.start = start;
            this.end = end;
            this.vector = vector;
            this.other = other;
            this.result = result;
        }

        @Override
        public void run() {
            for (int i = start; i <= end; i++) {
                result.setDataAt(i, vector.getDataAt(i) + other.getDataAt(i));
            }
        }
    }

    public Vector sum(Vector other) throws InterruptedException {
        if (getLength() != other.getLength()) {
            throw new IllegalArgumentException("The vectors have to be of the same length!");
        }

        Vector result = new Vector(getLength());
        // ceil(Vector.setLength / segLength)
        int threadsNeeded = (getLength() + SEG_LENGTH - 1) / SEG_LENGTH;
        Thread[] threads = new Thread[threadsNeeded];
        for (int i = 0; i < threadsNeeded - 1; i++) {
            threads[i] = new Thread(new SumHelper(i * SEG_LENGTH, (i + 1) * SEG_LENGTH - 1,
                                                    this, other, result));
            threads[i].start();
        }
        threads[threadsNeeded - 1] = new Thread(new SumHelper((threadsNeeded - 1) * SEG_LENGTH,
                getLength() - 1, this, other, result));
        threads[threadsNeeded - 1].start();

        for (Thread t : threads) {
            t.join();
        }

        return result;
    }

    public Vector sumSeq(Vector other) {
        if (getLength() != other.getLength()) {
            throw new IllegalArgumentException("The vectors have to be of the same length!");
        }

        Vector result = new Vector(getLength());
        for (int i = 0; i < getLength(); i++) {
            result.setDataAt(i, getDataAt(i) + other.getDataAt(i));
        }
        return result;
    }

    /* DOT PRODUCT */
    private static class DotHelper implements Runnable {
        private final int start;
        private final int end;
        private final Vector vector;
        private final Vector other;
        private int partialDotProduct;

        private DotHelper(int start, int end, Vector vector, Vector other) {
            this.start = start;
            this.end = end;
            this.vector = vector;
            this.other = other;
            partialDotProduct = 0;
        }

        public int getPartialDotProduct() {
            return partialDotProduct;
        }

        @Override
        public void run() {
            for (int i = start; i <= end; i++) {
                partialDotProduct += vector.getDataAt(i) * other.getDataAt(i);
            }
        }
    }

    public int dot(Vector other) throws InterruptedException {
        if (getLength() != other.getLength()) {
            throw new IllegalArgumentException("The vectors have to be of the same length!");
        }
        int result = 0;
        int threadsNeeded = (getLength() + SEG_LENGTH - 1) / SEG_LENGTH;
        Thread[] threads = new Thread[threadsNeeded];
        DotHelper[] dotHelpers = new DotHelper[threadsNeeded];

        for (int i = 0; i < threadsNeeded - 1; i++) {
            dotHelpers[i] = new DotHelper(i * SEG_LENGTH, (i + 1) * SEG_LENGTH - 1,
                    this, other);
            threads[i] = new Thread(dotHelpers[i]);
            threads[i].start();
        }
        dotHelpers[threadsNeeded - 1] = new DotHelper((threadsNeeded - 1) * SEG_LENGTH,
                getLength() - 1, this, other);
        threads[threadsNeeded - 1] = new Thread(dotHelpers[threadsNeeded - 1]);
        threads[threadsNeeded - 1].start();

        for (Thread t : threads) {
            t.join();
        }

        for (DotHelper dotHelper : dotHelpers) {
            result += dotHelper.getPartialDotProduct();
        }

        return result;
    }

    public int dotSeq(Vector other) {
        if (getLength() != other.getLength()) {
            throw new IllegalArgumentException("The vectors have to be of the same length!");
        }
        int result = 0;
        for (int i = 0; i < getLength(); i++) {
            result += getDataAt(i) * other.getDataAt(i);
        }

        return result;
    }

    public static void main(String[] args) {
        try {
            Vector a = randomVector(33);
            System.out.println(a);
            Vector b = randomVector(33);
            System.out.println(b);

            Vector sum1 = a.sum(b);
            System.out.println(sum1);
            Vector sum2 = a.sumSeq(b);
            System.out.println(sum2);
            System.out.println(sum1.equals(sum2));

            int dot1 = a.dot(b);
            System.out.println(dot1);
            int dot2 = a.dotSeq(b);
            System.out.println(dot2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Computation interrupted");
        }
    }
}
