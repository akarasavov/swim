package atk.app.member;

import atk.app.network.NetworkRequest;
import atk.app.network.NetworkResponse;
import atk.app.network.TcpRequest;
import atk.app.network.Transport;
import atk.app.network.protocol.FullStateSyncRequest;
import atk.app.network.protocol.FullStateSyncResponse;
import java.io.Closeable;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes incoming requests by listening received request channel
 */
public class NetworkRequestHandler implements Runnable, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(NetworkRequestHandler.class);
    private final MemberList memberList;
    private final Transport transport;
    private volatile boolean active = false;

    public NetworkRequestHandler(MemberList memberList, Transport transport) {
        this.memberList = memberList;
        this.transport = transport;
    }

    @Override
    public void run() {
        if (active) {
            throw new IllegalStateException("It is already run");
        }
        active = true;
        var receivedRequests = transport.getReceivedRequests();
        while (isActive()) {
            TcpRequest tcpRequest = receivedRequests.pull(Duration.ofSeconds(10));
            if (tcpRequest != null) {
                logger.debug("Start processing {}", tcpRequest);
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
        } else {
            throw new IllegalStateException("Unsupported message " + networkRequest);
        }
    }

    private void processFullStateSyncRequest(FullStateSyncRequest request,
                                             CompletableFuture<NetworkResponse> responseHandler) {
        var response = new FullStateSyncResponse(memberList.getMemberStates());
        var requestMap = Map.of(request.memberState().memberName, request.memberState());
        memberList.mergeWithoutConflictResolution(requestMap);

        responseHandler.complete(response);
    }
}
