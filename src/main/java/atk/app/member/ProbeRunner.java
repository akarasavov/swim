package atk.app.member;

import atk.app.lifecycle.ThreadSafeLifecycle;
import atk.app.network.netty.NetworkClient;
import atk.app.network.protocol.IndirectPingRequest;
import atk.app.network.protocol.NetworkResponseHandler;
import atk.app.network.protocol.PingRequest;
import atk.app.util.ConcurrencyUtil;
import atk.app.util.ExceptionUtil;
import atk.app.util.FutureUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ScheduledExecutorService executor;
    private final Duration probePeriod;
    private final Duration maximumRequestTimeout;
    private final NetworkClient networkClient;
    private final SuspectTimers suspectTimers;
    private final int indirectPingTargets;
    private final MemberName myName;
    private final NetworkResponseHandler responseHandler;
    private volatile ScheduledFuture<?> probeJobFuture;

    public ProbeRunner(NetworkResponseHandler responseHandler,
                       NetworkClient networkClient,
                       MemberList memberList,
                       SuspectTimers suspectTimers,
                       ExecutorService lifecycleExecutor,
                       Duration probePeriod,
                       Duration maximumRequestTimeout,
                       int indirectPingTargets) {
        super(lifecycleExecutor);
        this.responseHandler = responseHandler;
        this.networkClient = networkClient;
        this.myName = memberList.getMyName();
        this.memberList = memberList;
        this.executor = Executors.newScheduledThreadPool(1);
        this.probePeriod = probePeriod;
        this.maximumRequestTimeout = maximumRequestTimeout;
        this.suspectTimers = suspectTimers;
        this.indirectPingTargets = indirectPingTargets;
    }

    @Override
    protected void start0() {
        this.probeJobFuture = executor.scheduleAtFixedRate(this::probeARandomMember, 0, probePeriod.toMillis(), TimeUnit.MILLISECONDS);
        logger.debug("Start probe runner for {}", myName);
    }

    @Override
    protected void stop0() {
        while (!probeJobFuture.isDone()) {
            probeJobFuture.cancel(true);
            logger.debug("Cancel probe runner job for {}", myName);
            ExceptionUtil.ignoreThrownExceptions(() -> Thread.sleep(100), logger);
        }
    }

    @Override
    protected void close0() {
        start0();
        ConcurrencyUtil.shutdownExecutor(executor);
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
                } else {
                    memberList.makeMemberAlive(probeTarget.memberName);
                    logger.debug("{} successfully send indirect ping to {}", probeTarget.memberName, probeTarget.memberName);
                }
            }
        } else {
            memberList.makeMemberAlive(probeTarget.memberName);
            logger.debug("{} successfully ping {}.", myName, probeTarget.memberName);
        }
        logger.debug("{} Finish probing a {}", myName, probeTarget);
    }

    private boolean sendPingRequestToTargetMember(List<MemberList.MemberState> localMemberStates, MemberList.MemberState probeTarget) {
        var networkResponse = FutureUtil.getIfExists(networkClient.send(new PingRequest(localMemberStates), probeTarget.bindAddress, maximumRequestTimeout));
        if (networkResponse.isPresent()) {
            responseHandler.processNetworkResponse(networkResponse.get());
            return true;
        }
        return false;
    }

    private boolean sendIndirectPingToRandomMembers(MemberList.MemberState probeTarget, Set<MemberList.MemberState> indirectPingTargets, Duration probeDeadLine) {
        logger.debug("{} Pick {} for indirect probe of {}", myName, indirectPingTargets.stream().map(m -> m.memberName).collect(Collectors.toList()), probeTarget.memberName);
        if (indirectPingTargets.isEmpty()) {
            return false;
        }
        var targetsForIndirectPing = indirectPingTargets.stream().map(member -> member.bindAddress).collect(Collectors.toList());
        var requestFeatures = networkClient.send(new IndirectPingRequest(memberList.getMemberStates(), probeTarget.bindAddress), targetsForIndirectPing, probeDeadLine);
        //Ask K members to send ping to the probe member
        var responses = FutureUtil.getDoneResponsesThatCompleted(requestFeatures, probeDeadLine);
        if (!responses.isEmpty()) {
            logger.debug("{} Received {} responses", myName, responses.size());
            responses.forEach(feature -> responseHandler.processNetworkResponse(FutureUtil.get(feature)));
            return true;
        }
        return false;
    }

    public void suspectMember(MemberList.MemberState probeTarget) {
        logger.debug("{} Suspect member {}", myName, probeTarget.memberName);
        suspectTimers.suspectMember(probeTarget.memberName);
        memberList.suspectMember(probeTarget.memberName);
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
