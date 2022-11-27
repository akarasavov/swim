package atk.app.member;

import static atk.app.member.MemberList.MemberState.*;
import static atk.app.member.MemberList.MemberState.isSuspectLocalAliveMember;
import java.io.Serializable;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Local view of a members state. Class is thread-safe
 */
public class MemberList {

    private final Map<MemberName, MemberState> memberStates = new HashMap<>();
    private final SuspectTimers suspectTimers;

    public MemberList(SuspectTimers suspectTimers, MemberState myState) {
        this.suspectTimers = suspectTimers;

        memberStates.put(myState.memberName, myState);
    }

    /**
     * Merge current state with other state without resolving conflicts. Should be used only for full state sync
     */
    public synchronized void mergeWithoutConflictResolution(Map<MemberName, MemberState> otherMemberState) {
        memberStates.putAll(otherMemberState);
    }

    public synchronized void merge(Map<MemberName, MemberState> otherStates) {
        otherStates.forEach((memberName, remoteMemberState) -> {
            var localState = memberStates.get(memberName);
            // dead state override any state
            if (localState == null || remoteMemberState.stateType.isDead()) {
                //TODO remove member suspicious timer if its run
                suspectTimers.reviveMember(memberName);
                memberStates.put(memberName, remoteMemberState);
            } else if (isSuspectLocalAliveMember(localState, remoteMemberState)) {
                //TODO run suspect timer
                suspectTimers.suspectMember(memberName);
                //TODO if this is for me increment your local incarnation number
                memberStates.put(memberName, remoteMemberState);
            } else if (isReviveLocalSuspectedMember(localState, remoteMemberState)) {
                //TODO remove local suspected member
                suspectTimers.reviveMember(memberName);
                memberStates.put(memberName, remoteMemberState);
            } else if (localState.incarnation <= remoteMemberState.incarnation) {
                memberStates.put(memberName, remoteMemberState);
            }
        });
    }

    public synchronized List<MemberState> getMemberStates() {
        return new ArrayList<>(memberStates.values());
    }

    public static class MemberState implements Serializable {
        public final MemberName memberName;
        public final SocketAddress bindAddress;
        public final int incarnation; // last known incarnation number
        public final MemberStateType stateType;
        public final Instant updated; // Time last state changed
        //TODO - add max, min and curr supported versions

        public MemberState(MemberName memberName,
                           SocketAddress bindAddress,
                           int incarnation,
                           MemberStateType stateType) {
            this.memberName = memberName;
            this.bindAddress = bindAddress;
            this.incarnation = incarnation;
            this.stateType = stateType;
            this.updated = Instant.now();
        }

        static boolean isSuspectLocalAliveMember(MemberState local, MemberState remote) {
            return local.incarnation <= remote.incarnation &&
                    local.stateType == MemberStateType.ALIVE &&
                    remote.stateType == MemberStateType.SUSPECTED;
        }

        static boolean isReviveLocalSuspectedMember(MemberState local, MemberState remote) {
            return local.incarnation < remote.incarnation &&
                    local.stateType == MemberStateType.SUSPECTED &&
                    remote.stateType == MemberStateType.ALIVE;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MemberState that = (MemberState) o;
            return incarnation == that.incarnation && Objects.equals(memberName, that.memberName) && Objects.equals(bindAddress, that.bindAddress) && stateType == that.stateType && Objects.equals(updated, that.updated);
        }

        @Override
        public int hashCode() {
            return Objects.hash(memberName, bindAddress, incarnation, stateType, updated);
        }
    }

    public static enum MemberStateType implements Serializable {
        ALIVE, DEAD, SUSPECTED;


        public boolean isDead() {
            return this == DEAD;
        }
    }
}
