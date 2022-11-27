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

    private final WriteableChannel<MemberName> deadMemberChannel;
    private final ScheduledExecutorService executorService;
    private final Map<MemberName, ScheduledFuture<?>> timersMap = new ConcurrentHashMap<>();
    private final Duration deadTimeout;

    private volatile boolean closed = false;

    public SuspectTimers(WriteableChannel<MemberName> deadMemberChannel, ScheduledExecutorService executorService, Duration deadTimeout) {
        this.deadMemberChannel = deadMemberChannel;
        this.executorService = executorService;
        this.deadTimeout = deadTimeout;
    }

    public void suspectMember(MemberName memberName) {
        if (isClosed()) {
            return;
        }
        timersMap.putIfAbsent(memberName, executorService.schedule(() -> {
            deadMemberChannel.push(memberName);
        }, deadTimeout.toMillis(), TimeUnit.MILLISECONDS));
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
