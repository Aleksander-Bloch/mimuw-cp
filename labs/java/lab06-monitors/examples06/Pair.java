package examples06;

public class Pair<T> {

    private T first;
    private T second;

    public Pair(T first, T second) {
        this.first = first;
        this.second = second;
    }

    public void swap() {
        synchronized (this) {
            T a = first;
            first = second;
            second = a;
        }
    }

    public synchronized boolean areEqual() {
        return first.equals(second);
    }

}
