package solutions06;

import java.util.LinkedList;
import java.util.Queue;

public class BlockingQueue<T> {
    private final int capacity;
    private final Queue<T> queue;

    public BlockingQueue(int capacity) {
        assert capacity > 0;
        this.capacity = capacity;
        queue = new LinkedList<>();
    }

    public synchronized T take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        T element = queue.remove();
        notifyAll();
        return element;
    }

    public synchronized void put(T item) throws InterruptedException {
        while (queue.size() == capacity) {
            wait();
        }
        queue.add(item);
        notifyAll();
    }

    public synchronized int getSize() {
        return queue.size();
    }

    public int getCapacity() {
        return capacity;
    }
}