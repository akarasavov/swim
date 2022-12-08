package atk.app.network.protocol;

import atk.app.lifecycle.LifecycleStates;
import atk.app.lifecycle.ThreadSafeLifecycle;
import atk.app.member.MemberList;
import atk.app.network.NetworkResponse;
import atk.app.network.NetworkServer;
import atk.app.network.TcpRequest;
import atk.app.network.netty.NetworkClient;
import atk.app.util.ConcurrencyUtil;
import atk.app.util.channel.ReadableChannel;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes incoming requests by listening received request channel
 */
public class NetworkRequestHandler extends ThreadSafeLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(NetworkRequestHandler.class);
    private final MemberList memberList;
    private final NetworkClient networkClient;
    private final Duration requestMaximumTimeout;
    private final ExecutorService requestHandlerExecutor;
    private final ReadableChannel<TcpRequest> receivedRequestsChannel;
    private Future<?> taskFuture;

    public NetworkRequestHandler(ExecutorService lifecycleExecutor, MemberList memberList, NetworkServer<Void> networkServer, NetworkClient networkClient,
                                 Duration requestMaximumTimeout) {
        super(lifecycleExecutor);
        this.memberList = memberList;
        this.networkClient = networkClient;
        this.requestMaximumTimeout = requestMaximumTimeout;
        this.receivedRequestsChannel = networkServer.getReceivedRequests();
        this.requestHandlerExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void start0() {
        this.taskFuture = requestHandlerExecutor.submit(() -> {
            while (hasState(LifecycleStates.STARTED)) {
                TcpRequest tcpRequest = receivedRequestsChannel.pull(Duration.ofSeconds(10));
                if (tcpRequest != null) {
                    logger.debug("Start processing {}", tcpRequest.getClass());
                    processNetworkRequest(tcpRequest);
                }
            }
        });

    }

    @Override
    protected void stop0() {
        taskFuture.cancel(true);
    }

    @Override
    protected void close0() {
        ConcurrencyUtil.shutdownExecutor(requestHandlerExecutor);
    }

    /**
     * Processes network request. Processing shouldn't block the network request thread
     */
    private void processNetworkRequest(TcpRequest tcpRequest) {
        var networkRequest = tcpRequest.getRequest();
        if (networkRequest instanceof FullStateSyncRequest) {
            processFullStateSyncRequest((FullStateSyncRequest) networkRequest, tcpRequest.getResponseHandler());
        } else if (networkRequest instanceof PingRequest) {
            processPingRequest((PingRequest) networkRequest, tcpRequest.getResponseHandler());
        } else if (networkRequest instanceof IndirectPingRequest) {
            processIndirectPingRequest((IndirectPingRequest) networkRequest, tcpRequest.getResponseHandler());
        } else {
            logger.error("Received unsupported network request {}", networkRequest.getClass());
            throw new IllegalStateException("Unsupported message " + networkRequest);
        }
    }

    private void processFullStateSyncRequest(FullStateSyncRequest request, CompletableFuture<NetworkResponse> responseHandler) {
        var response = new FullStateSyncResponse(memberList.getMemberStates());
        var requestMap = Map.of(request.memberState().memberName, request.memberState());
        memberList.update(requestMap);

        responseHandler.complete(response);
    }

    private void processPingRequest(PingRequest request, CompletableFuture<NetworkResponse> responseHandler) {
        var response = new AckResponse(memberList.getMemberStates());

        var requestMap = request.memberStates().stream().collect(Collectors.toMap(k -> k.memberName, k -> k));
        memberList.update(requestMap);

        responseHandler.complete(response);
    }

    private void processIndirectPingRequest(IndirectPingRequest request, CompletableFuture<NetworkResponse> responseHandler) {
        //update your local state based on the request data
        var requestMap = request.memberStates().stream().collect(Collectors.toMap(k -> k.memberName, k -> k));
        memberList.update(requestMap);

        networkClient.send(new PingRequest(memberList.getMemberStates()), request.probeTargetAddress(), requestMaximumTimeout)
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        logger.error("Wasn't able to send {} to {}", request, request.probeTargetAddress());
                    } else if (response instanceof AckResponse) {
                        var ackResponseMap = ((AckResponse) response).memberStates().stream().collect(Collectors.toMap(k -> k.memberName, k -> k));
                        memberList.update(ackResponseMap);

                        //resend received response to the initiator of the indirect ping request
                        responseHandler.complete(response);
                    } else {
                        logger.error("Receive illegal response on indirect ping {}", response);
                        throw new IllegalStateException("Receive illegal response on indirect ping " + response);
                    }
                });
    }
}
