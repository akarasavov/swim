package atk.app.util.channel;

import java.io.Closeable;

public interface WriteableChannel<T> extends Closeable {

    /**
     * Push an element to the channel. If there is no free space block until it appears. Support N concurrent producers
     */
    void push(T element);

}
