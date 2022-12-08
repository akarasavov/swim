package atk.app.member;


import atk.app.lifecycle.LifecycleStates;
import atk.app.lifecycle.ThreadSafeLifecycle;
import atk.app.util.ConcurrencyUtil;
import atk.app.util.channel.WriteableChannel;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SuspectTimers extends ThreadSafeLifecycle {
    private final WriteableChannel<MemberName> suspectMemberChannel;
    private final ScheduledExecutorService executor;
    private final Map<MemberName, ScheduledFuture<?>> timersMap = new ConcurrentHashMap<>();
    private final Duration suspectedMemberDeadline;

    public SuspectTimers(ExecutorService lifecycleExecutor, WriteableChannel<MemberName> deadMembers, Duration suspectedMemberDeadline) {
        super(lifecycleExecutor);
        this.suspectMemberChannel = deadMembers;
        this.executor = Executors.newScheduledThreadPool(1);
        this.suspectedMemberDeadline = suspectedMemberDeadline;
    }

    @Override
    protected void start0() {

    }

    @Override
    protected void stop0() {
        var memberNames = timersMap.keySet();
        memberNames.stream().map(memberName -> timersMap.remove(memberNames))
                .filter(Objects::nonNull)
                .forEach(future -> future.cancel(true));
    }

    @Override
    protected void close0() {
        ConcurrencyUtil.shutdownExecutor(executor);
    }


    public void suspectMember(MemberName memberName) {
        verifyCurrentState(Set.of(LifecycleStates.STARTED));

        timersMap.putIfAbsent(memberName,
                // member will be marked as dead if suspectedMemberDeadline is violated
                executor.schedule(() -> suspectMemberChannel.push(memberName), suspectedMemberDeadline.toMillis(),
                        TimeUnit.MILLISECONDS)
        );
    }

    public void reviveMember(MemberName memberName) {
        verifyCurrentState(Set.of(LifecycleStates.STARTED));

        ScheduledFuture<?> future = timersMap.get(memberName);
        if (future != null) {
            future.cancel(false);
        }
    }
}
