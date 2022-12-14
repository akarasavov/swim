package atk.app.member;

import static atk.app.member.MemberList.MemberState.isReviveLocalSuspectedMember;
import static atk.app.member.MemberList.MemberState.isSuspectLocalAliveMember;
import static atk.app.member.MemberList.MemberState.isUpdateOfMemberStateWithTheSameType;
import static atk.app.util.FutureUtil.VOID;
import java.io.Serializable;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local view of a members state. Class is thread-safe
 */
public class MemberList {
    private static final Logger logger = LoggerFactory.getLogger(MemberList.class);
    private final Map<MemberName, MemberState> othersState = new HashMap<>();
    //protect other state from concurrent access
    private final ReentrantLock otherStateLock = new ReentrantLock();
    // my state is extracted in separate field because nobody can modify my state
    private final MemberState myState;
    // used t
    private final List<MemberState> roundRobinMemberStates = new ArrayList<>();
    private final ReentrantLock roundRobinMemberStatesLock = new ReentrantLock();
    private final RoundRobinStrategy roundRobinStrategy;

    public MemberList(MemberState myState) {
        this.myState = myState;
        this.roundRobinStrategy = new RoundRobinStrategy(roundRobinMemberStates);
    }

    public boolean suspectMember(MemberName memberName) {
        return callAndProtectBy(otherStateLock, () -> {
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
            putANewState(memberName, updatedState);
            return true;
        });
    }

    public boolean makeMemberAlive(MemberName memberName) {
        return callAndProtectBy(otherStateLock, () -> {
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
            putANewState(memberName, updatedState);
            return true;
        });
    }

    public boolean makeMemberDead(MemberName memberName) {
        return callAndProtectBy(otherStateLock, () -> {
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
            putANewState(memberName, updatedState);
            return true;
        });
    }

    public void update(Map<MemberName, MemberState> remoteStates) {
        callAndProtectBy(otherStateLock, () -> {
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
                    putANewState(memberName, remoteState);
                }
            });
            return VOID;
        });
    }

    private void putANewState(MemberName memberName, MemberState newState) {
        othersState.put(memberName, newState);
        callAndProtectBy(roundRobinMemberStatesLock, () -> {
            //member to be removed
            int memberIndex = -1;
            for (int i = 0; i < roundRobinMemberStates.size(); i++) {
                if (roundRobinMemberStates.get(i).memberName.equals(memberName)) {
                    memberIndex = i;
                    break;
                }
            }
            if (memberIndex != -1) {
                roundRobinMemberStates.remove(memberIndex);
            }
            roundRobinMemberStates.add(newState);
            return VOID;
        });
    }

    /**
     * This method is thread-safe because myState never change
     */
    public MemberName getMyName() {
        return myState.memberName;
    }

    public List<MemberState> getMemberStates() {
        return callAndProtectBy(otherStateLock, () -> {
            var memberStateCopy = new ArrayList<>(othersState.values());
            memberStateCopy.add(myState);
            return memberStateCopy;
        });
    }

    public List<MemberState> getMemberStateWithoutMe() {
        return callAndProtectBy(otherStateLock, () -> new ArrayList<>(othersState.values()));
    }

    public MemberState nextMemberToPing() {
        return callAndProtectBy(roundRobinMemberStatesLock, roundRobinStrategy::nextMember);
    }

    private <T> T callAndProtectBy(ReentrantLock lock, Supplier<T> supplier) {
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
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

    static class RoundRobinStrategy {
        private final List<MemberState> roundRobinMemberStates;
        private int nextIndex = 0;

        RoundRobinStrategy(List<MemberState> roundRobinMemberStates) {
            this.roundRobinMemberStates = roundRobinMemberStates;
        }

        /**
         * roundRobinMemberStatesLock should be taken before usage of this function
         */
        MemberState nextMember() {
            if (nextIndex == 0) {
                Collections.shuffle(roundRobinMemberStates);
            }
            //roundRobinMemberStates may contain less element than the nextIndex because of removal
            if (nextIndex >= roundRobinMemberStates.size()) {
                nextIndex = 0;
                return nextMember();
            }
            var result = roundRobinMemberStates.get(nextIndex);
            if (nextIndex + 1 >= roundRobinMemberStates.size()) {
                nextIndex = 0;
            } else {
                nextIndex++;
            }
            return result;
        }
    }
}
