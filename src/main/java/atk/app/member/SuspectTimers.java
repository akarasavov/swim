package atk.app.member;


import atk.app.util.channel.WriteableChannel;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SuspectTimers implements Closeable {

    private final WriteableChannel<MemberName> suspectMemberChannel;
    private final ScheduledExecutorService executorService;
    private final Map<MemberName, ScheduledFuture<?>> timersMap = new ConcurrentHashMap<>();
    private final Duration suspectedMemberDeadline;

    private volatile boolean closed = false;

    public SuspectTimers(WriteableChannel<MemberName> deadMembers, ScheduledExecutorService executorService, Duration suspectedMemberDeadline) {
        this.suspectMemberChannel = deadMembers;
        this.executorService = executorService;
        this.suspectedMemberDeadline = suspectedMemberDeadline;
    }

    public void suspectMember(MemberName memberName) {
        if (isClosed()) {
            return;
        }
        timersMap.putIfAbsent(memberName,
                // member will be marked as dead if suspectedMemberDeadline is violated
                executorService.schedule(() -> suspectMemberChannel.push(memberName), suspectedMemberDeadline.toMillis(),
                        TimeUnit.MILLISECONDS)
        );
    }

    public void reviveMember(MemberName memberName) {
        if (isClosed()) {
            return;
        }
        ScheduledFuture<?> future = timersMap.get(memberName);
        if (future != null) {
            future.cancel(false);
        }
    }

    private boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }
}
