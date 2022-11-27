package atk.app.util;

import java.util.concurrent.CompletableFuture;

public class FutureUtil {

    public static final Void VOID = null;

    public static CompletableFuture<Void> toVoidFuture(CompletableFuture<?> future) {
        return future.thenApply(o -> null);
    }
}
