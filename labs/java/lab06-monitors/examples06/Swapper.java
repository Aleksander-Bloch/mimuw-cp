package examples06;

import java.util.ArrayList;
import java.util.List;

public class Swapper<T> {

    private T value;
    private final List<T> history = new ArrayList<>();

    public Swapper(T val) {
        this.value = val;
        history.add(val);
    }

    public synchronized void ifEqualThenSet(T oldVal, T newVal) throws InterruptedException {
        while (!value.equals(oldVal)) {
            wait();
        }
        value = newVal;
        history.add(newVal);
        notifyAll();
    }

    public synchronized List<T> getHistory() {
        return history;
    }

}
