package atk.app.lifecycle;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

// TODO - All lifecycles should be concurrent state machines that returns completable feature for start() and stop()
public interface Lifecycle<T> extends Closeable {
    CompletableFuture<T> start();

    CompletableFuture<T> stop();

    /**
     * Close will block on caller thread until lifecycle is closed
     * */
    @Override
    default void close()  {
        stop();
    }

}
