package atk.app.member;

import atk.app.lifecycle.ThreadSafeLifecycle;
import atk.app.network.netty.NetworkClient;
import atk.app.network.protocol.AckResponse;
import atk.app.network.protocol.IndirectPingRequest;
import atk.app.network.protocol.PingRequest;
import atk.app.util.ExceptionUtil;
import atk.app.util.FutureUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProbeRunner extends ThreadSafeLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(ProbeRunner.class);
    private final MemberList memberList;
    private final ScheduledExecutorService scheduledExecutor;
    private final Duration probePeriod;
    private final Duration pingTimeout;
    private final NetworkClient networkClient;
    private final SuspectTimers suspectTimers;
    private final int indirectPingTargets;
    private final MemberName myName;
    private volatile ScheduledFuture<?> probeJobFuture;

    public ProbeRunner(NetworkClient networkClient,
                       MemberList memberList,
                       SuspectTimers suspectTimers,
                       ScheduledExecutorService scheduledExecutor,
                       ExecutorService lifecycleExecutor,
                       Duration probePeriod,
                       Duration networkRequestMaximumDuration,
                       int indirectPingTargets) {
        super(lifecycleExecutor);
        if (networkRequestMaximumDuration.compareTo(probePeriod) >= 0) {
            throw new IllegalArgumentException("Ping timeout should be less then probe period");
        }
        this.networkClient = networkClient;
        this.myName = memberList.getMyName();
        this.memberList = memberList;
        this.scheduledExecutor = scheduledExecutor;
        this.probePeriod = probePeriod;
        this.pingTimeout = networkRequestMaximumDuration;
        this.suspectTimers = suspectTimers;
        this.indirectPingTargets = indirectPingTargets;
    }

    @Override
    protected void start0() {
        this.probeJobFuture = scheduledExecutor.scheduleAtFixedRate(this::probeARandomMember, 0, probePeriod.toMillis(), TimeUnit.MILLISECONDS);
        logger.debug("Start probe runner for {}", myName);
    }

    @Override
    protected void stop0() {
        while (probeJobFuture.isDone()) {
            probeJobFuture.cancel(true);
            logger.debug("Cancel probe runner job for {}", myName);
            ExceptionUtil.ignoreThrownExceptions(() -> Thread.sleep(100), logger);
        }
    }

    @Override
    protected void close0() {
        start0();
    }

    //TODO - strategy for picking probe member should be extracted from separate class
    private void probeARandomMember() {
        var probeDeadLine = Instant.now().plus(probePeriod);
        var localMemberStates = memberList.getMemberStateWithoutMe();
        if (localMemberStates.isEmpty()) {
            return;
        }
        var probeTargetIndex = getKRandomIndexesInRange(1, localMemberStates.size()).iterator().next();
        var probeTarget = localMemberStates.get(probeTargetIndex);
        logger.debug("{} Start probing {}", myName, probeTarget.memberName);
        if (!sendPingRequestToTargetMember(memberList.getMemberStates(), probeTarget)) {
            var now = Instant.now();
            if (now.isAfter(probeDeadLine)) {
                suspectMember(probeTarget);
            } else {
                int numberOfIndirectPingTargets = Math.min(this.indirectPingTargets, localMemberStates.size() - 1);
                var indirectPingTargets = getKRandomIndexesInRange(numberOfIndirectPingTargets, localMemberStates.size(), index -> !index.equals(probeTargetIndex))
                        .stream()
                        .map(localMemberStates::get)
                        .collect(Collectors.toSet());
                if (!sendIndirectPingToRandomMembers(probeTarget, indirectPingTargets, Duration.between(now, probeDeadLine))) {
                    suspectMember(probeTarget);
                }
            }
        }
        logger.debug("{} Finish probing a {}", myName, probeTarget);
    }

    private boolean sendPingRequestToTargetMember(List<MemberList.MemberState> localMemberStates, MemberList.MemberState probeTarget) {
        var networkResponse = FutureUtil.getIfExists(networkClient.send(new PingRequest(localMemberStates), probeTarget.bindAddress),
                pingTimeout);
        if (networkResponse.isPresent()) {
            processAckResponse((AckResponse) networkResponse.get());
            return true;
        }
        return false;
    }

    private boolean sendIndirectPingToRandomMembers(MemberList.MemberState probeTarget, Set<MemberList.MemberState> indirectPingTargets, Duration probeDeadLine) {
        logger.debug("{} Pick {} for indirect probe of {}", myName, indirectPingTargets.stream().map(m -> m.memberName).collect(Collectors.toList()), probeTarget.memberName);
        var targetsForIndirectPing = indirectPingTargets.stream().map(member -> member.bindAddress).collect(Collectors.toList());
        var requestFeatures = networkClient.send(new IndirectPingRequest(memberList.getMemberStates(), probeTarget.bindAddress), targetsForIndirectPing);
        //Ask K members to send ping to the probe member
        var responses = FutureUtil.getDoneResponsesThatCompleted(requestFeatures, probeDeadLine);
        if (!responses.isEmpty()) {
            logger.debug("{} Received {} responses", myName, responses.size());
            responses.forEach(feature -> processAckResponse((AckResponse) FutureUtil.get(feature, Duration.ZERO)));
            return true;
        }
        return false;
    }

    public void suspectMember(MemberList.MemberState probeTarget) {
        logger.debug("{} Suspect member {}", myName, probeTarget.memberName);
        suspectTimers.suspectMember(probeTarget.memberName);
        memberList.suspectMember(probeTarget.memberName);
    }

    private void processAckResponse(AckResponse ackResponse) {
        var remoteMembershipMap = ackResponse.memberStates().stream().collect(Collectors.toMap(k -> k.memberName, k -> k));
        memberList.update(remoteMembershipMap);
        logger.debug("{} Processed {}", myName, ackResponse);
    }

    private Set<Integer> getKRandomIndexesInRange(int k, int range) {
        return getKRandomIndexesInRange(k, range, integer -> true);
    }

    private Set<Integer> getKRandomIndexesInRange(int k, int range, Predicate<Integer> noValues) {
        Set<Integer> result = new HashSet<>();
        while (result.size() < k) {
            var random = new Random();
            var nextId = random.nextInt(range);
            if (noValues.test(nextId)) {
                result.add(nextId);
            }
        }
        return result;
    }
}
