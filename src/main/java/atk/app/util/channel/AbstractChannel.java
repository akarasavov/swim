package atk.app.util.channel;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

abstract class AbstractChannel<T> implements Channel<T> {

    private final BlockingQueue<T> queue;
    private volatile boolean closed;

    public AbstractChannel(BlockingQueue<T> queue) {
        this.queue = queue;
    }

    @Override
    public T pull(Duration waitDuration) {
        if (closed) {
            return null;
        }
        try {
            return queue.poll(waitDuration.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            return null;
        }
    }

    @Override
    public void push(T element) {
        while (!closed) {
            try {
                boolean result = queue.offer(element, 10, TimeUnit.SECONDS);
                if (result) {
                    return;
                }
            } catch (InterruptedException ignored) {
                return;
            }
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }
}
