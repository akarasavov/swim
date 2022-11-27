package atk.app.util.channel;

import java.time.Duration;

public interface ReadableChannel<T> {

    /**
     * Pull an element from a channel. Operation is concurrent safe for N consumers
     *
     * @return - if there is an element in the channel it will be pulled immediately otherwise calling thread will wait until there is an element in the channel.
     * If this time is more than waitDuration client will receive null
     *
     * */
    T pull(Duration waitDuration);
}
