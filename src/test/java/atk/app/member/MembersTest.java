package atk.app.member;

import static atk.app.util.MemberTestUtil.TestMember;
import static atk.app.util.channel.ConcurrencyUtil.waitDefaultTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import atk.app.util.MemberTestUtil;
import java.io.IOException;
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
    void afterAll() throws IOException {
        memberTestUtil.close();
    }

    @Test
    void memberJoinAnotherMember() throws ExecutionException, InterruptedException, TimeoutException {
        try (TestMember m1 = memberTestUtil.createMember("a1");
             TestMember m2 = memberTestUtil.createMember("a2");
             TestMember m3 = memberTestUtil.createMember("a3")) {
            waitDefaultTime(m1.member().start());
            waitDefaultTime(m2.member().start());
            waitDefaultTime(m3.member().start());

            //when m1 joins m2
            waitDefaultTime(m1.member().joinMember(m2.config().bindAddress));

            // then m1 and m2 have the same member lists
            assertEquals(m1.member().getMemberList(), m2.member().getMemberList());

            // when m3 joins m2
            waitDefaultTime(m3.member().joinMember(m2.config().bindAddress));
            // then m3 and m2 have the same member lists
            assertEquals(m2.member().getMemberList(), m3.member().getMemberList());
            //then m3 and m1 don't have the same member lists
            assertNotEquals(m1.member().getMemberList(), m3.member().getMemberList());
        }
    }

}
