package atk.app.member;

import static atk.app.util.MemberTestUtil.TestMember;
import static atk.app.util.ConcurrencyUtil.awaitForCompletion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import atk.app.util.MemberStateUtil;
import atk.app.util.MemberTestUtil;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        try (TestMember m1 = memberTestUtil.createMember("a1");
             TestMember m2 = memberTestUtil.createMember("a2");
             TestMember m3 = memberTestUtil.createMember("a3")) {
            awaitForCompletion(m1.member().start());
            awaitForCompletion(m2.member().start());
            awaitForCompletion(m3.member().start());

            //when m1 joins m2
            awaitForCompletion(m1.member().joinMember(m2.config().bindAddress));

            // then m1 and m2 have the same member lists
            assertThat(m1.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m2.member().getMemberList());

            // when m3 joins m2
            awaitForCompletion(m3.member().joinMember(m2.config().bindAddress));
            // then m3 and m2 have the same member lists
            assertThat(m2.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m3.member().getMemberList());
            //then m3 and m1 don't have the same member lists
            var member3State = MemberStateUtil.getMemberWithName(m2.member().getMemberList(), new MemberName("a3"));
            assertThat(m1.member().getMemberList())
                    .doesNotContain(member3State);
        }
    }

    @Test
    void membersListShouldConvergeAfterTheyStartProbeEachOther() throws ExecutionException, InterruptedException, TimeoutException {
        try (TestMember m1 = memberTestUtil.createMember("a1");
             TestMember m2 = memberTestUtil.createMember("a2");
             TestMember m3 = memberTestUtil.createMember("a3")) {
            awaitForCompletion(m1.member().start());
            awaitForCompletion(m2.member().start());
            awaitForCompletion(m3.member().start());

            //when m1 joins m2
            awaitForCompletion(m1.member().joinMember(m2.config().bindAddress));
            //when m3 joins m2
            awaitForCompletion(m3.member().joinMember(m2.config().bindAddress));
            //wait member list to converge
            Thread.sleep(Duration.ofSeconds(10).toMillis());
            // then m3 and m2 have the same member lists
            assertThat(m2.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m3.member().getMemberList());
            // then m1 and m2 have the same member lists
            assertThat(m2.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m1.member().getMemberList());
        }
    }

    @Test
    void memberShouldSuspectAnotherMemberIfCantContactIt() throws ExecutionException, InterruptedException, TimeoutException {
        try (TestMember m1 = memberTestUtil.createMember("a1");
             TestMember m2 = memberTestUtil.createMember("a2")) {
            awaitForCompletion(m1.member().start());
            awaitForCompletion(m2.member().start());

            //when m1 joins m2
            awaitForCompletion(m1.member().joinMember(m2.config().bindAddress));
            // then m1 and m2 have the same member lists
            assertThat(m2.member().getMemberList()).containsExactlyInAnyOrderElementsOf(m1.member().getMemberList());
            // then m2 stops
            awaitForCompletion(m2.member().stop());
            //wait member list to converge
            Thread.sleep(Duration.ofSeconds(10).toMillis());
            //then m1 suspect member m2
            //TODO
            //then m2 suspect member m1
            //TODO
        }
    }


}
