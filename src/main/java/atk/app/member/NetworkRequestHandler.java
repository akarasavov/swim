package atk.app.member;

import atk.app.network.NetworkRequest;
import atk.app.network.NetworkResponse;
import atk.app.network.NetworkServer;
import atk.app.network.TcpRequest;
import atk.app.network.protocol.AckResponse;
import atk.app.network.protocol.FullStateSyncRequest;
import atk.app.network.protocol.FullStateSyncResponse;
import atk.app.network.protocol.PingRequest;
import java.io.Closeable;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes incoming requests by listening received request channel
 */
public class NetworkRequestHandler implements Runnable, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(NetworkRequestHandler.class);
    private final MemberList memberList;
    private final NetworkServer<Void> networkServer;
    private volatile boolean active = false;

    public NetworkRequestHandler(MemberList memberList, NetworkServer<Void> networkServer) {
        this.memberList = memberList;
        this.networkServer = networkServer;
    }

    @Override
    public void run() {
        if (active) {
            throw new IllegalStateException("It is already run");
        }
        active = true;
        var receivedRequests = networkServer.getReceivedRequests();
        while (isActive()) {
            TcpRequest tcpRequest = receivedRequests.pull(Duration.ofSeconds(10));
            if (tcpRequest != null) {
                logger.debug("Start processing {}", tcpRequest.getClass());
                var networkRequest = tcpRequest.getRequest();
                processNetworkRequest(tcpRequest, networkRequest);
            }
        }
    }

    @Override
    public void close() {
        if (!isActive()) {
            throw new IllegalStateException("It is already closed");
        }
        active = false;
    }

    private boolean isActive() {
        return active;
    }

    private void processNetworkRequest(TcpRequest tcpRequest, NetworkRequest networkRequest) {
        if (networkRequest instanceof FullStateSyncRequest) {
            processFullStateSyncRequest((FullStateSyncRequest) networkRequest, tcpRequest.getResponseHandler());
        } else if (networkRequest instanceof PingRequest) {
            processPingRequest((PingRequest) networkRequest, tcpRequest.getResponseHandler());
        } else {
            logger.error("Received unsupported network request {}", networkRequest.getClass());
            throw new IllegalStateException("Unsupported message " + networkRequest);
        }
    }

    private void processFullStateSyncRequest(FullStateSyncRequest request,
                                             CompletableFuture<NetworkResponse> responseHandler) {
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
}
