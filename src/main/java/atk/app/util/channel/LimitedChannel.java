package atk.app.util.channel;

import java.util.concurrent.ArrayBlockingQueue;

public class LimitedChannel<T> extends AbstractChannel<T> {

    public LimitedChannel(int capacity) {
        super(new ArrayBlockingQueue<>(capacity));
    }
}
