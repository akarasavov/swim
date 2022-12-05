package atk.app.util.channel;

import java.io.Closeable;
import java.time.Duration;

public interface ReadableChannel<T> extends Closeable {

    /**
     * Pull an element from a channel. Operation is concurrent safe for N consumers
     *
     * @return - if there is an element in the channel it will be pulled immediately otherwise calling thread will wait until there is an element in the channel.
     * If this time is more than waitDuration client will receive null
     *
     * */
    T pull(Duration waitDuration);

    /**
     * Pull an element from a channel. Operation is concurrent safe for N consumers
     *
     * @return - return the first element from the channel. If the channel is empty then block until there is an element to pull.
     * */
    T pull();
}
