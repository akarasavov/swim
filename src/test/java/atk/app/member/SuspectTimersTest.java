package atk.app.member;

import static atk.app.util.ConcurrencyUtil.awaitForCompletion;
import static org.assertj.core.api.Assertions.assertThat;
import atk.app.util.ConcurrencyUtil;
import atk.app.util.MemberListUtil;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class SuspectTimersTest {

    @Test
    void shouldMarkMemberAsDeadIfDeadTimeoutIsExceeded() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        var executorService = Executors.newCachedThreadPool();
        var memberList = MemberListUtil.createRandomList(2);
        try (SuspectTimers suspectTimers = new SuspectTimers(executorService, memberList, Duration.ofSeconds(1))) {
            awaitForCompletion(suspectTimers.start());

            //when suspect member
            var targetMember = memberList.getMemberStateWithoutMe().get(0);
            suspectTimers.suspectMember(targetMember.memberName);
            memberList.suspectMember(targetMember.memberName);

            //then
            Thread.sleep(2000);
            assertThat(memberList.getMemberStateWithoutMe().get(0).isDead()).isTrue();
        } finally {
            ConcurrencyUtil.shutdownExecutor(executorService);
        }
    }

    @Test
    void memberShouldntBeMarkedAsDeadIfItIsRevivedBeforeTimeoutIsExceeded() throws ExecutionException, InterruptedException, TimeoutException {
        var executorService = Executors.newCachedThreadPool();
        var memberList = MemberListUtil.createRandomList(2);
        try (SuspectTimers suspectTimers = new SuspectTimers(executorService, memberList, Duration.ofSeconds(2))) {
            awaitForCompletion(suspectTimers.start());
            var targetMember = memberList.getMemberStateWithoutMe().get(0);

            //when suspect member
            suspectTimers.suspectMember(targetMember.memberName);
            //when then revive member
            suspectTimers.unSuspectMember(targetMember.memberName);

            //then
            assertThat(memberList.getMemberStateWithoutMe().get(0).isAlive()).isTrue();
        } finally {
            ConcurrencyUtil.shutdownExecutor(executorService);
        }
    }

    @Test
    void memberShouldBeMarkedAsDeadIfItIsRevivedAfterTimeoutIsExceeded() throws InterruptedException, IOException, ExecutionException, TimeoutException {
        var executorService = Executors.newCachedThreadPool();
        var memberList = MemberListUtil.createRandomList(2);
        try (var suspectTimers = new SuspectTimers(executorService, memberList, Duration.ofMillis(500))) {
            awaitForCompletion(suspectTimers.start());
            var targetMember = memberList.getMemberStateWithoutMe().get(0);

            //when suspect member
            suspectTimers.suspectMember(targetMember.memberName);
            memberList.suspectMember(targetMember.memberName);

            //when after 600 milliseconds member is revived
            Thread.sleep(600);
            suspectTimers.unSuspectMember(targetMember.memberName);


            //then
            Thread.sleep(2000);
            assertThat(memberList.getMemberStateWithoutMe().get(0).isDead()).isTrue();
        } finally {
            ConcurrencyUtil.shutdownExecutor(executorService);
        }
    }


}
