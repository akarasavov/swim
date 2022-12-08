package atk.app.util;

import atk.app.network.NetworkResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FutureUtil {

    public static final Void VOID = null;

    public static CompletableFuture<Void> toVoidFuture(CompletableFuture<?> future) {
        return future.thenApply(o -> null);
    }

    public static <V> V get(CompletableFuture<V> future) {
        return getIfExists(future)
                .orElseThrow(() -> new IllegalStateException(future + " haven't completed"));
    }

    public static <V> Optional<V> getIfExists(CompletableFuture<V> future) {
        try {
            return Optional.of(future.get());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static Set<CompletableFuture<NetworkResponse>> getDoneResponsesThatCompleted(List<CompletableFuture<NetworkResponse>> completableFutures,
                                                                                        Duration completionDuration) {
        var deadline = Instant.now().plus(completionDuration);
        List<CompletableFuture<NetworkResponse>> internalCopy = new ArrayList<>(completableFutures);
        while (Instant.now().isBefore(deadline)) {
            try {
                CompletableFuture.anyOf(internalCopy.toArray(new CompletableFuture[completableFutures.size()]))
                        .get(completionDuration.toMillis(), TimeUnit.MILLISECONDS);
                return internalCopy.stream().filter(CompletableFuture::isDone).collect(Collectors.toSet());
            } catch (Exception ex) {
                var failed = internalCopy.stream().filter(CompletableFuture::isCompletedExceptionally).collect(Collectors.toSet());
                internalCopy.removeAll(failed);
            }
        }
        return Set.of();
    }
}
