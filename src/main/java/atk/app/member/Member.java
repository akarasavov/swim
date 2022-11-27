package atk.app.member;

import static atk.app.member.MemberList.MemberState;
import static atk.app.member.MemberList.MemberStateType;
import static atk.app.util.ExceptionUtil.ignoreThrownExceptions;
import static atk.app.util.FutureUtil.VOID;
import atk.app.model.Lifecycle;
import atk.app.network.NetworkResponse;
import atk.app.network.Transport;
import atk.app.network.protocol.AckResponse;
import atk.app.network.protocol.FullStateSyncRequest;
import atk.app.network.protocol.FullStateSyncResponse;
import atk.app.network.protocol.PingRequest;
import atk.app.util.channel.Channel;
import atk.app.util.channel.NotLimitedChannel;
import java.io.Closeable;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Member implements Lifecycle<Void> {
    private static final Logger logger = LoggerFactory.getLogger(Member.class);
    private final ExecutorService memberExecutor;
    private final Transport transport;
    private final Config config;
    //thread safe representation of member list
    private final MemberList memberList;
    //resources that need to be release on close
    private final List<Closeable> closeables = new ArrayList<>();
    //mutable state
    private volatile boolean stopped = true;
    //run threads
    private final NetworkRequestHandler requestHandler;

    public Member(Config config, ExecutorService memberExecutor, ScheduledExecutorService suspectTimersExecutor, Transport transport) {
        this.memberExecutor = memberExecutor;
        this.transport = transport;
        this.config = config;
        Channel<MemberName> deadMemberChannel = new NotLimitedChannel<>();
        var suspectTimers = new SuspectTimers(deadMemberChannel, suspectTimersExecutor, config.deadMemberTimeout);
        // initial state of every member is alive state
        this.memberList = new MemberList(suspectTimers,
                new MemberState(config.memberName, config.bindAddress, 0, MemberStateType.ALIVE));
        this.requestHandler = new NetworkRequestHandler(memberList, transport);

        // register all resource that needs to be released
        closeables.add(requestHandler);
        closeables.add(transport);
        closeables.add(deadMemberChannel);
        closeables.add(suspectTimers);

//        setAliveYourself();
    }


    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                stopped = false;
                //TODO - this parameter should be configurable
                transport.start().get(30, TimeUnit.SECONDS);
                memberExecutor.submit(requestHandler);
                logger.debug("Member {} started", config.memberName.name());
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Wasn't able to start a member {}", config.memberName.name());
                close();
            }
            return VOID;
        }, memberExecutor);

    }

    @Override
    public CompletableFuture<Void> stop() {
        return internalClose();
    }

    @Override
    public void close() {
        try {
            internalClose().get();
            logger.debug("Member with name {} was successfully stopped", config.memberName.name());
        } catch (InterruptedException | ExecutionException ignored) {
        }
    }

    private CompletableFuture<Void> internalClose() {
        return CompletableFuture.supplyAsync(() -> {
            stopped = true;
            closeables.forEach(c -> ignoreThrownExceptions(c::close));
            return VOID;
        }, memberExecutor);
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
        return transport.send(networkRequest, target)
                .whenComplete((networkResponse, throwable) -> // TODO - handle correctly the exception
                        processFullStateSyncResponse((FullStateSyncResponse) networkResponse));
    }

    // TODO - response request handlers should be extracted into separate class
    private void processFullStateSyncResponse(FullStateSyncResponse response) {
        var responseMap = response.memberStates().stream().collect(Collectors.toMap(k -> k.memberName, k -> k));
        memberList.mergeWithoutConflictResolution(responseMap);
    }


    private void probeAMember() {
        var localMemberStates = memberList.getMemberStates();
        var nextMemberToProbe = localMemberStates.get(new Random().nextInt(localMemberStates.size()));
        //run suspicious timer
        transport.send(new PingRequest(localMemberStates), nextMemberToProbe.bindAddress)
                .whenComplete((networkResponse, throwable) -> {
                    if (networkResponse != null) {
                        processAckResponse((AckResponse) networkResponse);
                    }
                });
    }

    private void processAckResponse(AckResponse ackResponse) {
        // remove suspicious timer
    }
}
