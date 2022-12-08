package atk.app.lifecycle;

import static atk.app.util.FutureUtil.VOID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ThreadSafeLifecycle implements Lifecycle<Void> {
    protected final ExecutorService lifecycleExecutor;
    private volatile LifecycleStates currentState = LifecycleStates.INITIAL;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final Object syncObject = new Object();

    /**
     * @param lifecycleExecutor - executes all lifecycle methods. The client of this class is responsible for shutdowning the executor
     */
    public ThreadSafeLifecycle(ExecutorService lifecycleExecutor) {
        this.lifecycleExecutor = lifecycleExecutor;
    }

    @Override
    public final CompletableFuture<Void> start() {
        return CompletableFuture.supplyAsync(() -> {
            changeState(LifecycleStates.STARTED, Set.of(LifecycleStates.INITIAL, LifecycleStates.STOPPED));
            start0();
            return VOID;
        }, lifecycleExecutor);
    }

    @Override
    public final CompletableFuture<Void> stop() {
        return CompletableFuture.supplyAsync(() -> {
            changeState(LifecycleStates.STOPPED, Set.of(LifecycleStates.STARTED));
            stop0();
            return VOID;
        }, lifecycleExecutor);
    }

    @Override
    public final void close() {
        try {
            CompletableFuture.supplyAsync(() -> {
                synchronized (syncObject) {
                    if (!hasState(LifecycleStates.STOPPED)) {
                        stop0();
                    }
                }
                changeState(LifecycleStates.CLOSED, Set.of(LifecycleStates.STARTED, LifecycleStates.STOPPED));
                close0();
                return VOID;
            }, lifecycleExecutor).get();
        } catch (InterruptedException | ExecutionException ignored) {
        }
    }

    protected abstract void start0();

    protected abstract void stop0();

    protected abstract void close0();

    private void changeState(LifecycleStates newState, Set<LifecycleStates> expectedStates) {
        synchronized (syncObject) {
            verifyCurrentState(expectedStates);
            var prevState = currentState;
            currentState = newState;
            logger.debug("Change state from {} to {}", prevState, newState);
        }
    }

    protected void verifyCurrentState(Set<LifecycleStates> expectedStates) {
        synchronized (syncObject) {
            if (!expectedStates.contains(currentState)) {
                throw new IllegalStateException(String.format("Current state %s is not in expected states %s for object " + getClass(), currentState, expectedStates));
            }
        }
    }

    protected boolean hasState(LifecycleStates expectedState) {
        synchronized (syncObject) {
            return currentState == expectedState;
        }
    }
}
