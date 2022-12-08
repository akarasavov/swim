package atk.app.member;

import static atk.app.util.ConcurrencyUtil.awaitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import atk.app.util.ConcurrencyUtil;
import atk.app.util.MemberStateUtil;
import atk.app.util.channel.BoundedChannel;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class SuspectTimersTest {

    @Test
    void shouldMarkMemberAsDeadIfDeadTimeoutIsExceeded() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        var deadMemberChannel = new BoundedChannel<MemberName>(1);
        var executorService = Executors.newCachedThreadPool();
        try (SuspectTimers suspectTimers = new SuspectTimers(executorService, deadMemberChannel, Duration.ofSeconds(1))) {
            awaitForCompletion(suspectTimers.start());
            var suspectedMember = MemberStateUtil.randomMemberName();

            //when suspect member
            suspectTimers.suspectMember(suspectedMember);

            //then
            var deadMember = deadMemberChannel.pull(Duration.ofSeconds(2));
            assertEquals(suspectedMember, deadMember);
        } finally {
            ConcurrencyUtil.shutdownExecutor(executorService);
        }
    }

    @Test
    void memberShouldntBeMarkedAsDeadIfItIsRevivedBeforeTimeoutIsExceeded() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        var deadMemberChannel = new BoundedChannel<MemberName>(1);
        var executorService = Executors.newCachedThreadPool();
        try (SuspectTimers suspectTimers = new SuspectTimers(executorService, deadMemberChannel, Duration.ofSeconds(2))) {
            awaitForCompletion(suspectTimers.start());
            var suspectedMember = MemberStateUtil.randomMemberName();

            //when suspect member
            suspectTimers.suspectMember(suspectedMember);
            //when then revive member
            suspectTimers.reviveMember(suspectedMember);

            //then
            var deadMember = deadMemberChannel.pull(Duration.ofSeconds(2));
            assertNull(deadMember);
        } finally {
            ConcurrencyUtil.shutdownExecutor(executorService);
        }
    }

    @Test
    void memberShouldBeMarkedAsDeadIfItIsRevivedAfterTimeoutIsExceeded() throws InterruptedException, IOException, ExecutionException, TimeoutException {
        var deadMemberChannel = new BoundedChannel<MemberName>(1);
        var executorService = Executors.newCachedThreadPool();
        try (var suspectTimers = new SuspectTimers(executorService, deadMemberChannel, Duration.ofMillis(500))) {
            awaitForCompletion(suspectTimers.start());
            var suspectedMember = MemberStateUtil.randomMemberName();

            //when suspect member
            suspectTimers.suspectMember(suspectedMember);

            //when after 600 milliseconds member is revived
            Thread.sleep(600);
            suspectTimers.reviveMember(suspectedMember);


            //then
            var deadMember = deadMemberChannel.pull(Duration.ZERO);
            assertEquals(suspectedMember, deadMember);
        } finally {
            ConcurrencyUtil.shutdownExecutor(executorService);
        }
    }


}
