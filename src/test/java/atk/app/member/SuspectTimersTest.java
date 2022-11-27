package atk.app.member;

import static atk.app.util.channel.ConcurrencyUtil.shutdownExecutor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import atk.app.util.MemberStateUtil;
import atk.app.util.channel.LimitedChannel;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuspectTimersTest {

    private ScheduledExecutorService executor;

    @BeforeEach
    void setUp() {
        this.executor = Executors.newScheduledThreadPool(1);
    }

    @AfterEach
    void tearDown() {
        shutdownExecutor(executor);
    }

    @Test
    void shouldMarkMemberAsDeadIfDeadTimeoutIsExceeded() {
        var deadMemberChannel = new LimitedChannel<MemberName>(1);
        var suspectTimers = new SuspectTimers(deadMemberChannel, executor, Duration.ofSeconds(1));
        var suspectedMember = MemberStateUtil.randomMemberName();

        //when suspect member
        suspectTimers.suspectMember(suspectedMember);

        //then
        var deadMember = deadMemberChannel.pull(Duration.ofSeconds(2));
        assertEquals(suspectedMember, deadMember);
    }

    @Test
    void memberShouldntBeMarkedAsDeadIfItIsRevivedBeforeTimeoutIsExceeded(){
        var deadMemberChannel = new LimitedChannel<MemberName>(1);
        var suspectTimers = new SuspectTimers(deadMemberChannel, executor, Duration.ofSeconds(2));
        var suspectedMember = MemberStateUtil.randomMemberName();

        //when suspect member
        suspectTimers.suspectMember(suspectedMember);
        //when then revive member
        suspectTimers.reviveMember(suspectedMember);

        //then
        var deadMember = deadMemberChannel.pull(Duration.ofSeconds(2));
        assertNull(deadMember);
    }

    @Test
    void memberShouldBeMarkedAsDeadIfItIsRevivedAfterTimeoutIsExceeded() throws InterruptedException {
        var deadMemberChannel = new LimitedChannel<MemberName>(1);
        var suspectTimers = new SuspectTimers(deadMemberChannel, executor, Duration.ofMillis(500));
        var suspectedMember = MemberStateUtil.randomMemberName();

        //when suspect member
        suspectTimers.suspectMember(suspectedMember);

        //when after 600 milliseconds member is revived
        Thread.sleep(600);
        suspectTimers.reviveMember(suspectedMember);


        //then
        var deadMember = deadMemberChannel.pull(Duration.ZERO);
        assertEquals(suspectedMember, deadMember);
    }


}
