package atk.app.member;

import static atk.app.member.MemberList.MemberState;
import static atk.app.member.MemberList.MemberStateType;
import static atk.app.util.ExceptionUtil.ignoreThrownExceptions;
import static atk.app.util.FutureUtil.VOID;
import atk.app.lifecycle.ThreadSafeLifecycle;
import atk.app.network.NetworkResponse;
import atk.app.network.NetworkServer;
import atk.app.network.netty.NetworkClient;
import atk.app.network.protocol.FullStateSyncRequest;
import atk.app.network.protocol.FullStateSyncResponse;
import atk.app.util.ConcurrencyUtil;
import atk.app.util.channel.BoundedChannel;
import atk.app.util.channel.Channel;
import java.io.Closeable;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Member extends ThreadSafeLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(Member.class);
    private final ScheduledExecutorService suspectTimersExecutor;
    private final Config config;
    //thread safe representation of member list
    private final MemberList memberList;
    //resources that need to be release on close
    private final List<Closeable> closeables = new ArrayList<>();
    //network API
    private final NetworkServer<Void> networkServer;
    private final NetworkClient networkClient;

    public Member(Config config, ExecutorService lifecycleExecutor, ScheduledExecutorService suspectTimersExecutor,
                  NetworkServer<Void> networkServer, NetworkClient networkClient) {
        super(lifecycleExecutor);
        this.suspectTimersExecutor = suspectTimersExecutor;
        this.networkServer = networkServer;
        this.networkClient = networkClient;
        this.config = config;

        // initial state of every member is alive state
        this.memberList = new MemberList(new MemberState(config.memberName, config.bindAddress, 0, MemberStateType.ALIVE));
    }

    @Override
    protected void start0() {
        try {
            //TODO - this parameter should be configurable
            networkServer.start().get(10, TimeUnit.SECONDS);
            //create request handler
            var requestHandlerExecutor = Executors.newSingleThreadExecutor();
            var requestHandler = new NetworkRequestHandler(memberList, networkServer);
            requestHandlerExecutor.submit(requestHandler);
            closeables.add(requestHandler);
            closeables.add(() -> ConcurrencyUtil.shutdownExecutor(requestHandlerExecutor));

            // create suspect timers
            Channel<MemberName> deadMemberChannel = new BoundedChannel<>(10);
            //TODO - Make SuspectTimers ThreadSafeLifecycle
            var suspectTimers = new SuspectTimers(deadMemberChannel, suspectTimersExecutor, config.suspectedMemberDeadline);
            closeables.add(deadMemberChannel);
            closeables.add(suspectTimers);

            //
            var probeRunner = new ProbeRunner(networkClient, memberList, suspectTimers, suspectTimersExecutor,
                    lifecycleExecutor, config.probePeriod, config.networkRequestMaximumDuration,
                    config.indirectPingTargets);
            probeRunner.start();
            closeables.add(probeRunner);

            logger.debug("Member {} started", config.memberName.name());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Wasn't able to start a member {}", config.memberName.name(), e);
        }
    }

    @Override
    protected void stop0() {
        try {
            networkServer.stop().get(10, TimeUnit.SECONDS);
        }catch (Exception ex){
            logger.error("Wasn't able to stop netty server", ex);
        }
        internalClose();
    }

    @Override
    protected void close0() {
        internalClose();
        logger.debug("Member with name {} was successfully stopped", config.memberName.name());
    }

    private void internalClose() {
        closeables.forEach(c -> ignoreThrownExceptions(c::close, logger));
    }

    public List<MemberState> getMemberList() {
        return memberList.getMemberStates();
    }

    public CompletableFuture<NetworkResponse> joinMember(SocketAddress target) {
        var currentState = memberList.getMemberStates();
        if (currentState.size() != 1) {
            return CompletableFuture.failedFuture(new IllegalStateException("Current state of member list should contains only me"));
        }
        var networkRequest = new FullStateSyncRequest(currentState.iterator().next());
        //TODO - this parameter should be configurable
        return networkClient.send(networkRequest, target)
                .whenComplete((networkResponse, throwable) -> // TODO - handle correctly the exception
                        processFullStateSyncResponse((FullStateSyncResponse) networkResponse));

    }

    // TODO - response request handlers should be extracted into separate class
    private void processFullStateSyncResponse(FullStateSyncResponse response) {
        var responseMap = response.memberStates().stream().collect(Collectors.toMap(k -> k.memberName, k -> k));
        memberList.update(responseMap);
    }
}
