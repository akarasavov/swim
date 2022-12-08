package atk.app.member;

import static atk.app.util.ConcurrencyUtil.awaitForCompletion;
import static atk.app.util.MemberStateUtil.getMemberWithName;
import static atk.app.util.MemberTestUtil.TestMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import atk.app.util.MemberTestUtil;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

//TODO - all Thread.sleep in all test should be replaced with await
class MembersTest {

    private MemberTestUtil memberTestUtil;

    @BeforeEach
    void setUp() {
        this.memberTestUtil = new MemberTestUtil();
    }

    @AfterEach
    void afterAll() {
        memberTestUtil.close();
    }

    @Test
    void memberJoinAnotherMember() throws ExecutionException, InterruptedException, TimeoutException {
        try (TestMember m1 = memberTestUtil.createMember("m1");
             TestMember m2 = memberTestUtil.createMember("m2");
             TestMember m3 = memberTestUtil.createMember("m3")) {
            awaitForCompletion(m1.member().start());
            awaitForCompletion(m2.member().start());
            awaitForCompletion(m3.member().start());

            //when m1 joins m2
            awaitForCompletion(m1.member().joinToMember(m2.config().bindAddress));

            // then m1 and m2 have the same member lists
            assertThat(m1.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m2.member().getMemberList());

            // when m3 joins m2
            awaitForCompletion(m3.member().joinToMember(m2.config().bindAddress));
            // then m3 and m2 have the same member lists
            assertThat(m2.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m3.member().getMemberList());
            //then m3 and m1 don't have the same member lists
            var member3State = getMemberWithName(m2.member().getMemberList(), new MemberName("m3"));
            assertThat(m1.member().getMemberList())
                    .doesNotContain(member3State);
        }
    }

    @Test
    void membersListShouldConvergeAfterTheyStartProbeEachOther() throws ExecutionException, InterruptedException, TimeoutException {
        var probePeriod = Duration.ofSeconds(4);
        var networkRequestTimeout = Duration.ofSeconds(1);
        try (TestMember m1 = memberTestUtil.createMember("m1", probePeriod, networkRequestTimeout);
             TestMember m2 = memberTestUtil.createMember("m2", probePeriod, networkRequestTimeout);
             TestMember m3 = memberTestUtil.createMember("m3", probePeriod, networkRequestTimeout)) {
            awaitForCompletion(m1.member().start());
            awaitForCompletion(m2.member().start());
            awaitForCompletion(m3.member().start());

            //when m1 joins m2
            awaitForCompletion(m1.member().joinToMember(m2.config().bindAddress));
            //when m3 joins m2
            awaitForCompletion(m3.member().joinToMember(m2.config().bindAddress));
            //wait member list to converge
            Thread.sleep(probePeriod.multipliedBy(2).toMillis());
            // then m3 and m2 have the same member lists
            assertThat(m2.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m3.member().getMemberList());
            // then m1 and m2 have the same member lists
            assertThat(m2.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m1.member().getMemberList());
        }
    }

    @Test
    void memberShouldSuspectAnotherMemberIfCantContactIt() throws ExecutionException, InterruptedException, TimeoutException {
        var probePeriod = Duration.ofSeconds(2);
        var networkRequestTimeout = Duration.ofMillis(100);
        try (TestMember m1 = memberTestUtil.createMember("m1", probePeriod, networkRequestTimeout);
             TestMember m2 = memberTestUtil.createMember("m2", probePeriod, networkRequestTimeout)) {
            awaitForCompletion(m1.member().start());
            awaitForCompletion(m2.member().start());

            //when m1 joins m2
            awaitForCompletion(m1.member().joinToMember(m2.config().bindAddress));
            // then m1 and m2 have the same member lists
            assertThat(m2.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m1.member().getMemberList());
            // then m2 stops
            awaitForCompletion(m2.member().stop());
            //wait member list to converge
            Thread.sleep(probePeriod.multipliedBy(2).toMillis());
            //then m1 suspect member m2
            assertTrue(getMemberWithName(m1.member().getMemberList(), new MemberName("m2")).stateType.isSuspected());
        }
    }

    @Test
    void memberShouldBeMarkedAsAliveIfAnotherMemberCanContactItAndSuspectDeadlineIsNotViolated() throws ExecutionException, InterruptedException, TimeoutException {
        var probePeriod = Duration.ofSeconds(4);
        var networkRequestTimeout = Duration.ofSeconds(1);
        var suspectMemberDeadline = Duration.ofSeconds(8);
        try (TestMember m1 = memberTestUtil.createMember("m1", probePeriod, suspectMemberDeadline, networkRequestTimeout);
             TestMember m2 = memberTestUtil.createMember("m2", probePeriod, suspectMemberDeadline, networkRequestTimeout)) {
            awaitForCompletion(m1.member().start());
            awaitForCompletion(m2.member().start());

            //when m1 joins m2
            awaitForCompletion(m1.member().joinToMember(m2.config().bindAddress));
            // then m1 and m2 have the same member lists
            assertThat(m2.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m1.member().getMemberList());
            // then m1 knows about m2
            assertThat(m1.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m2.member().getMemberList());
            // then m2 stops
            awaitForCompletion(m2.member().stop());
            //wait member list to converge
            Thread.sleep(probePeriod.multipliedBy(2).toMillis());
            //then m1 suspect member m2
            assertTrue(getMemberWithName(m1.member().getMemberList(), new MemberName("m2")).stateType.isSuspected());
            //then m2 starts
            awaitForCompletion(m2.member().start());
            //wait member list to converge
            Thread.sleep(probePeriod.multipliedBy(2).toMillis());
            //then m2 is marked as alive
            assertTrue(getMemberWithName(m1.member().getMemberList(), new MemberName("m2")).stateType.isAlive());
        }
    }
}
