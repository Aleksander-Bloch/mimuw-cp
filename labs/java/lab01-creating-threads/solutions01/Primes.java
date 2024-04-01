package solutions01;

public class Primes {

    private static final int END = 10000;
    private static final int NONE = 0;
    private static final int FOUND = 1;

    private static volatile int foundDivisor;

    public static boolean areThreadsAlive(Thread[] threads) {
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPrime(int n) {
        foundDivisor = NONE;
        int sqrt = (int) Math.sqrt(n);
        for (int p : new int[] { 2, 3, 5, 7, 11, 13, 17, 19, 23, 29 }) {
            if (p > sqrt) {
                return true;
            }
            if (n % p == 0) {
                return false;
            }
        }
        int[] starts = { 31, 37, 41, 43, 47, 49, 53, 59 };
        // FIXME: implement here and remove the line below
        Thread[] threads = new Thread[starts.length];
        for (int i = 0; i < starts.length; i++) {
            threads[i] = new Thread(new Helper(starts[i], n));
            threads[i].start();
        }

        while(areThreadsAlive(threads)) {
            // empty
        }

        return foundDivisor == NONE;
    }

    // FIXME: adding a class for helper threads may be convenient

    public static class Helper implements Runnable {
        private final int startPrime;
        private final int numToCheck;

        public Helper(int startPrime, int numToCheck) {
            this.startPrime = startPrime;
            this.numToCheck = numToCheck;
        }

        @Override
        public void run() {
            int sqrt = (int) Math.sqrt(numToCheck);
            for (int i = startPrime; i <= sqrt && foundDivisor == NONE; i += 30) {
                if (numToCheck % i == 0) {
                    foundDivisor = FOUND;
                    break;
                }
            }
        }
    }
    
    public static void main(String[] args) {
        int primesCount = 0;
        for (int i = 2; i <= END; ++i) {
            if (isPrime(i)) {
                ++primesCount;
            }
        }
        System.out.println(primesCount);
        assert(primesCount == 1229);
    }

}
