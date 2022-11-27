package atk.app.util.channel;

public interface WriteableChannel<T> {

    /**
     * Push an element to the channel. Support N concurrent producers
     */
    void push(T element);

}
