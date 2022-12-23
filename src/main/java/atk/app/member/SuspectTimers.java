package atk.app.member;


import atk.app.lifecycle.LifecycleStates;
import atk.app.lifecycle.ThreadSafeLifecycle;
import atk.app.util.ConcurrencyUtil;
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
    private final ScheduledExecutorService executor;
    private final Map<MemberName, ScheduledFuture<?>> timersMap = new ConcurrentHashMap<>();
    private final Duration suspectedMemberDeadline;
    private final MemberList memberList;

    public SuspectTimers(ExecutorService lifecycleExecutor, MemberList memberList, Duration suspectedMemberDeadline) {
        super(lifecycleExecutor);
        this.memberList = memberList;
        this.executor = Executors.newScheduledThreadPool(1);
        this.suspectedMemberDeadline = suspectedMemberDeadline;
    }

    @Override
    protected void start0() {

    }

    @Override
    protected void stop0() {
        var memberNames = timersMap.keySet();
        memberNames.stream().map(timersMap::remove)
                .filter(Objects::nonNull)
                .forEach(future -> future.cancel(true));
    }

    @Override
    protected void close0() {
        ConcurrencyUtil.shutdownExecutor(executor);
    }


    public void suspectMember(MemberName memberName) {
        verifyCurrentState(Set.of(LifecycleStates.STARTED));

        if (timersMap.containsKey(memberName)) {
            logger.warn("{} is already suspected", memberName);
            return;
        }
        logger.debug("Start suspected timer for {}", memberName);
        // member will be marked as dead if suspectedMemberDeadline is violated
        ScheduledFuture<?> future = executor.schedule(() -> markMemberAsDead(memberName), suspectedMemberDeadline.toMillis(),
                TimeUnit.MILLISECONDS);
        timersMap.put(memberName, future);
    }

    public void unSuspectMember(MemberName memberName) {
        verifyCurrentState(Set.of(LifecycleStates.STARTED));

        ScheduledFuture<?> future = timersMap.remove(memberName);
        if (future == null) {
            logger.warn("There is not time related to {}", memberName);
            return;
        }
        logger.debug("Stop suspected timer for {}", memberName);
        future.cancel(false);
    }

    private void markMemberAsDead(MemberName memberName) {
        logger.debug("Attempt to mark {} as dead", memberName);
        if (memberList.makeMemberDead(memberName)) {
            logger.debug("{} is marked as dead", memberName);
        }
    }
}
