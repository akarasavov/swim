package atk.app.member;

import static atk.app.member.MemberList.MemberState;
import static atk.app.member.MemberList.MemberStateType;
import static atk.app.util.ExceptionUtil.ignoreThrownExceptions;
import atk.app.lifecycle.ThreadSafeLifecycle;
import atk.app.network.NetworkResponse;
import atk.app.network.NetworkServer;
import atk.app.network.netty.NetworkClient;
import atk.app.network.protocol.FullStateSyncRequest;
import atk.app.network.protocol.NetworkRequestHandler;
import atk.app.network.protocol.NetworkResponseHandler;
import atk.app.util.channel.BoundedChannel;
import atk.app.util.channel.Channel;
import java.io.Closeable;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Member extends ThreadSafeLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(Member.class);
    private final Config config;
    //thread safe representation of member list
    private final MemberList memberList;
    //resources that need to be release on close
    private final List<Closeable> closeables = new ArrayList<>();
    //network API
    private final NetworkServer<Void> networkServer;
    private final NetworkClient networkClient;
    private final NetworkResponseHandler networkResponseHandler;
    private final NetworkRequestHandler requestHandler;
    private final SuspectTimers suspectTimers;
    private final ProbeRunner probeRunner;

    public Member(Config config, ExecutorService lifecycleExecutor,
                  NetworkServer<Void> networkServer, NetworkClient networkClient) {
        super(lifecycleExecutor);
        this.networkServer = networkServer;
        this.networkClient = networkClient;
        this.config = config;
        // initial state of every member is alive state
        this.memberList = new MemberList(new MemberState(config.memberName, config.bindAddress, 0, MemberStateType.ALIVE));
        this.networkResponseHandler = new NetworkResponseHandler(memberList);
        this.requestHandler = new NetworkRequestHandler(lifecycleExecutor, memberList, networkServer, networkClient, config.networkRequestMaximumDuration);
        closeables.add(requestHandler);
        // create suspect timers
        Channel<MemberName> deadMemberChannel = new BoundedChannel<>(10);
        this.suspectTimers = new SuspectTimers(lifecycleExecutor, deadMemberChannel, config.suspectedMemberDeadline);
        closeables.add(deadMemberChannel);
        closeables.add(suspectTimers);
        //create probe runner
        this.probeRunner = new ProbeRunner(networkResponseHandler, networkClient, memberList, suspectTimers,
                lifecycleExecutor, config.probePeriod, config.networkRequestMaximumDuration, config.indirectPingTargets);
        closeables.add(probeRunner);
    }

    @Override
    protected void start0() {
        try {
            networkServer.start().get(10, TimeUnit.SECONDS);
            requestHandler.start().get(10, TimeUnit.SECONDS);
            suspectTimers.start().get(10, TimeUnit.SECONDS);
            probeRunner.start().get(10, TimeUnit.SECONDS);
            logger.debug("Member {} was started", config.memberName.name());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Wasn't able to start a member {}", config.memberName.name(), e);
            throw new RuntimeException("Wasn't able to start a member " + config.memberName.name(), e);
        }
    }

    @Override
    protected void stop0() {
        try {
            networkServer.stop().get(10, TimeUnit.SECONDS);
            requestHandler.stop().get(10, TimeUnit.SECONDS);
            suspectTimers.stop().get(10, TimeUnit.SECONDS);
            probeRunner.stop().get(10, TimeUnit.SECONDS);
            logger.debug("Member {} was stopped", memberList.getMyName());
        } catch (Exception ex) {
            logger.error("Wasn't able to stop " + memberList.getMyName(), ex);
        }
    }

    @Override
    protected void close0() {
        internalClose();
        logger.debug("Member with name {} was successfully closed", config.memberName.name());
    }

    private void internalClose() {
        closeables.forEach(c -> ignoreThrownExceptions(c::close, logger));
    }

    public List<MemberState> getMemberList() {
        return memberList.getMemberStates();
    }

    public CompletableFuture<NetworkResponse> joinToMember(SocketAddress target) {
        // when there is one member then this means that it hasn't joined any group
        var currentState = memberList.getMemberStates();
        if (currentState.size() != 1) {
            // member can join only one member group
            return CompletableFuture.failedFuture(new IllegalStateException("Current state of member list should contains only me"));
        }
        var networkRequest = new FullStateSyncRequest(currentState.iterator().next());
        return networkClient.send(networkRequest, target, config.networkRequestMaximumDuration)
                .whenComplete((networkResponse, throwable) ->
                        networkResponseHandler.processNetworkResponse(networkResponse, throwable, "Wasn't able to join member with address " + target));

    }
}
