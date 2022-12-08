package atk.app.member;

import static atk.app.util.MemberStateUtil.aliveMember;
import static atk.app.util.MemberStateUtil.updateIncarnationNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemberListTest {

    private final MemberList.MemberState me = aliveMember();
    private MemberList memberList;

    @BeforeEach
    void setUp() {
        this.memberList = new MemberList(me);
    }

    @Test
    void addNewAliveMember() {
        //when new alive member is observed
        var aliveMember = aliveMember();
        memberList.update(Map.of(aliveMember.memberName, aliveMember));

        //then member state contains two alive members
        assertThat(memberList.getMemberStates()).containsExactlyInAnyOrder(me, aliveMember);
    }

    @Test
    void markAliveMemberAsDead() {
        var aliveMember = aliveMember();
        memberList.update(Map.of(aliveMember.memberName, aliveMember));

        //when an alive member is marked as dead
        var deadMember = aliveMember.makeMemberDead();
        memberList.update(Map.of(aliveMember.memberName, deadMember));

        //then current member is expected to be dead
        assertThat(memberList.getMemberStates()).containsExactlyInAnyOrder(me, deadMember);
    }

    @Test
    void suspectAnAliveMember() {
        var aliveMember = aliveMember();
        memberList.update(Map.of(aliveMember.memberName, aliveMember));

        //when an alive member is marked as suspect in the same incarnation
        var suspectMemberWithTheSameIncarnationNumber = aliveMember.suspectMember();
        memberList.update(Map.of(aliveMember.memberName, suspectMemberWithTheSameIncarnationNumber));

        //then current member is expected to be suspect
        assertThat(memberList.getMemberStates()).containsExactlyInAnyOrder(me, suspectMemberWithTheSameIncarnationNumber);

        //when an alive member is marked as suspect in the next incarnation
        var suspectMemberWithBiggerIncarnationNumber = updateIncarnationNumber(aliveMember, aliveMember.incarnation + 1);
        memberList.update(Map.of(aliveMember.memberName, suspectMemberWithBiggerIncarnationNumber));

        //then current member is expected to be suspect
        assertThat(memberList.getMemberStates()).containsExactlyInAnyOrder(me, suspectMemberWithBiggerIncarnationNumber);
    }

    @Test
    void cantSuspectMemberWithOldIncarnation() {
        var aliveMember = aliveMember();
        memberList.update(Map.of(aliveMember.memberName, aliveMember));

        //when alive member is marked as suspect in the old incarnation
        var suspectMemberWithOldIncarnation = updateIncarnationNumber(aliveMember.suspectMember(), aliveMember.incarnation - 1);
        memberList.update(Map.of(aliveMember.memberName, suspectMemberWithOldIncarnation));

        //then current member is expected to be alive
        assertThat(memberList.getMemberStates()).containsExactlyInAnyOrder(me, aliveMember);
    }


}
