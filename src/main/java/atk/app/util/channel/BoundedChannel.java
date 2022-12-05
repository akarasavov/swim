package atk.app.util.channel;

import java.util.concurrent.ArrayBlockingQueue;

public class BoundedChannel<T> extends AbstractChannel<T> {

    public BoundedChannel(int capacity) {
        super(new ArrayBlockingQueue<>(capacity));
    }
}
