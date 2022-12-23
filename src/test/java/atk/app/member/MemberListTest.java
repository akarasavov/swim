package atk.app.member;

import static atk.app.util.MemberStateUtil.aliveMember;
import static atk.app.util.MemberStateUtil.copyAndChangeState;
import static atk.app.util.MemberStateUtil.deadMember;
import static atk.app.util.MemberStateUtil.updateIncarnationNumber;
import static org.assertj.core.api.Assertions.assertThat;
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
        //given an alive member
        var aliveMember = aliveMember();
        memberList.update(Map.of(aliveMember.memberName, aliveMember));

        //when an alive member is marked as dead
        var deadMember = copyAndChangeState(aliveMember, MemberList.MemberStateType.DEAD);
        memberList.update(Map.of(aliveMember.memberName, deadMember));

        //then current member is expected to be dead
        assertThat(memberList.getMemberStates()).containsExactlyInAnyOrder(me, deadMember);
    }

    @Test
    void suspectAnAliveMember() {
        //given an alive member
        var aliveMember = aliveMember();
        memberList.update(Map.of(aliveMember.memberName, aliveMember));

        //when an alive member is marked as suspect in the same incarnation
        var suspectMemberWithTheSameIncarnationNumber = aliveMember.tryToSuspectMember();
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
        //given an alive member
        var aliveMember = aliveMember();
        memberList.update(Map.of(aliveMember.memberName, aliveMember));

        //when alive member is marked as suspect in the old incarnation
        var suspectMemberWithOldIncarnation = updateIncarnationNumber(aliveMember.tryToSuspectMember(), aliveMember.incarnation - 1);
        memberList.update(Map.of(aliveMember.memberName, suspectMemberWithOldIncarnation));

        //then current member is expected to be alive
        assertThat(memberList.getMemberStates()).containsExactlyInAnyOrder(me, aliveMember);
    }

    @Test
    void cantMakeDeadMemberSuspectedOrAlive(){
        //given an alive member
        var deadMember = deadMember();
        memberList.update(Map.of(deadMember.memberName, deadMember));

        //when there is an attempt to mark dead member as alive
        var aliveMember = copyAndChangeState(deadMember, MemberList.MemberStateType.ALIVE);
        memberList.update(Map.of(aliveMember.memberName, aliveMember));

        //then member should be in dead state
        assertThat(memberList.getMemberStates()).containsExactlyInAnyOrder(me, deadMember);

        //when there is an attempt to mark dead member as suspected
        var suspected = copyAndChangeState(deadMember, MemberList.MemberStateType.ALIVE);
        memberList.update(Map.of(suspected.memberName, suspected));

        //then member should be in dead state
        assertThat(memberList.getMemberStates()).containsExactlyInAnyOrder(me, deadMember);
    }
}
