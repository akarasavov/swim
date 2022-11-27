package atk.app.util.channel;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ConcurrencyUtil {

    public static <T> T waitDefaultTime(CompletableFuture<T> future)
            throws ExecutionException, InterruptedException, TimeoutException {
        return future.get(20, TimeUnit.SECONDS);
    }

    public static void shutdownExecutor(ExecutorService executor){
        executor.shutdownNow();
        var duration = Duration.ofSeconds(30);
        try {
            var terminated = executor.awaitTermination(duration.toSeconds(), TimeUnit.SECONDS);
            if (!terminated) {
                throw new IllegalStateException("Wasn't able to terminate for " + duration);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
