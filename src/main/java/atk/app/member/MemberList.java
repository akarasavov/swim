package atk.app.member;

import static atk.app.member.MemberList.MemberState.isReviveLocalSuspectedMember;
import static atk.app.member.MemberList.MemberState.isSuspectLocalAliveMember;
import static atk.app.member.MemberList.MemberState.isUpdateOfMemberStateWithTheSameType;
import java.io.Serializable;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local view of a members state. Class is thread-safe
 */
public class MemberList {
    private static final Logger logger = LoggerFactory.getLogger(MemberList.class);
    private final Map<MemberName, MemberState> othersState = new HashMap<>();
    private final MemberState myState;

    public MemberList(MemberState myState) {
        this.myState = myState;
    }

    public synchronized boolean suspectMember(MemberName memberName) {
        var memberState = othersState.get(memberName);
        if (memberState == null) {
            return false;
        }
        var updatedState = memberState.tryToSuspectMember();
        if (updatedState.equals(memberState)) {
            logger.warn("{} wasn't able to suspect member {}. It's state is {}", memberState.memberName, memberName, updatedState);
            return false;
        }
        logger.debug("{} suspected member {}", myState.memberName, memberName);
        othersState.put(memberName, updatedState);
        return true;
    }

    public synchronized boolean makeMemberAlive(MemberName memberName) {
        var memberState = othersState.get(memberName);
        if (memberState == null) {
            return false;
        }
        var updatedState = memberState.tryToAliveMember();
        if (updatedState.equals(memberState)) {
            logger.warn("{} wasn't able to make member alive {}. It's state is {}", memberState.memberName, memberName, updatedState);
            return false;
        }
        logger.debug("{} made a member alive {}", myState.memberName, memberName);
        othersState.put(memberName, updatedState);
        return true;
    }

    public synchronized boolean makeMemberDead(MemberName memberName) {
        var memberState = othersState.get(memberName);
        if (memberState == null) {
            return false;
        }
        var updatedState = memberState.tryToKillMember();
        if (updatedState.equals(memberState)) {
            logger.warn("{} wasn't able to kill a member {}. It's state is {}", memberState.memberName, memberName, updatedState);
            return false;
        }
        logger.debug("{} killed a member {}.", myState.memberName, memberName);
        othersState.put(memberName, updatedState);
        return true;
    }

    public synchronized void update(Map<MemberName, MemberState> remoteStates) {
        remoteStates.forEach((memberName, remoteState) -> {
            //TODO - what action you need to do if you are in suspected state
            //only I can update my state
            if (memberName.equals(myState.memberName)) {
                return;
            }
            var localState = othersState.get(memberName);
            if (localState == null ||
                    remoteState.stateType.isDead() ||
                    isSuspectLocalAliveMember(localState, remoteState) ||
                    isReviveLocalSuspectedMember(localState, remoteState) ||
                    isUpdateOfMemberStateWithTheSameType(localState, remoteState)) {
                othersState.put(memberName, remoteState);
            }
        });
    }

    public synchronized MemberName getMyName() {
        return myState.memberName;
    }

    public synchronized List<MemberState> getMemberStates() {
        var memberStateCopy = new ArrayList<>(othersState.values());
        memberStateCopy.add(myState);
        return memberStateCopy;
    }

    public synchronized List<MemberState> getMemberStateWithoutMe() {
        return new ArrayList<>(othersState.values());
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
            this(memberName, bindAddress, incarnation, stateType, Instant.now());
        }

        public MemberState(MemberName memberName,
                           SocketAddress bindAddress,
                           int incarnation,
                           MemberStateType stateType,
                           Instant updated) {
            this.memberName = memberName;
            this.bindAddress = bindAddress;
            this.incarnation = incarnation;
            this.stateType = stateType;
            this.updated = updated;
        }

        public MemberState tryToSuspectMember() {
            if (isAlive()) {
                return new MemberState(memberName, bindAddress, incarnation, MemberStateType.SUSPECTED, Instant.now());
            }
            return this;
        }

        public MemberState tryToKillMember() {
            if (isSuspected()) {
                return new MemberState(memberName, bindAddress, incarnation, MemberStateType.DEAD, Instant.now());
            }
            return this;
        }

        public MemberState tryToAliveMember() {
            if (isSuspected()) {
                return new MemberState(memberName, bindAddress, incarnation, MemberStateType.ALIVE, Instant.now());
            }
            return this;
        }

        public boolean isAlive() {
            return stateType.isAlive();
        }

        public boolean isSuspected() {
            return stateType.isSuspected();
        }

        public boolean isDead() {
            return stateType.isDead();
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

        static boolean isUpdateOfMemberStateWithTheSameType(MemberState local, MemberState remote) {
            return local.stateType == remote.stateType && local.incarnation < remote.incarnation;
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

        @Override
        public String toString() {
            return "MemberState{" +
                    "memberName=" + memberName +
                    ", bindAddress=" + bindAddress +
                    ", incarnation=" + incarnation +
                    ", stateType=" + stateType +
                    ", updated=" + updated +
                    '}';
        }
    }

    public enum MemberStateType implements Serializable {
        ALIVE, DEAD, SUSPECTED;


        public boolean isDead() {
            return this == DEAD;
        }

        public boolean isSuspected() {
            return this == SUSPECTED;
        }

        public boolean isAlive() {
            return this == ALIVE;
        }
    }
}
