package atk.app.network.netty;

import atk.app.lifecycle.Lifecycle;
import atk.app.network.NetworkRequest;
import atk.app.network.NetworkResponse;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface NetworkClient {

    CompletableFuture<NetworkResponse> send(NetworkRequest request, SocketAddress targetAddress);

    List<CompletableFuture<NetworkResponse>> send(NetworkRequest request, List<SocketAddress> targetAddress);
}
