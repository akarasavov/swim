package atk.app.network;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface NetworkClient {

    CompletableFuture<NetworkResponse> send(NetworkRequest request, SocketAddress targetAddress, Duration responseMaxTimeout);

    List<CompletableFuture<NetworkResponse>> send(NetworkRequest request, List<SocketAddress> targetAddresses, Duration responseMaxTimeout);
}
