package atk.app.network;

import atk.app.model.Lifecycle;
import atk.app.util.channel.ReadableChannel;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

//TODO - Implement UDP transport
public interface Transport extends Lifecycle<Void> {

    ReadableChannel<TcpRequest> getReceivedRequests();

    CompletableFuture<NetworkResponse> send(NetworkRequest request, SocketAddress targetAddress);
}
