package atk.app.network.netty;

import atk.app.network.NetworkRequest;
import atk.app.network.NetworkResponse;
import atk.app.network.TcpRequest;
import atk.app.network.Transport;
import atk.app.util.channel.Channel;
import atk.app.util.channel.ReadableChannel;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class NettyTransport implements Transport {

    private final Channel<TcpRequest> receivedRequests;
    private final NettyServer nettyServer;
    private final ExecutorService nettyTransportExecutor;
    private final Duration establishConnectionDeadline;

    public NettyTransport(int bindPort, Channel<TcpRequest> receivedRequestChannel,
                          Duration establishConnectionDeadline,
                          ExecutorService nettyTransportExecutor) {
        this.receivedRequests = receivedRequestChannel;
        //TODO - have a different executors for client a server connections
        this.nettyServer = new NettyServer(bindPort, this.receivedRequests, nettyTransportExecutor);
        this.establishConnectionDeadline = establishConnectionDeadline;
        this.nettyTransportExecutor = nettyTransportExecutor;

    }

    @Override
    public ReadableChannel<TcpRequest> getReceivedRequests() {
        return receivedRequests;
    }

    @Override
    public CompletableFuture<NetworkResponse> send(NetworkRequest request, SocketAddress targetAddress) {
        NettyClient nettyClient = new NettyClient(targetAddress, nettyTransportExecutor, establishConnectionDeadline);
        return nettyClient.start()
                .thenCompose(unused -> nettyClient.sendMessage(request))
                .whenComplete((unused, throwable) -> nettyClient.close());
    }

    @Override
    public CompletableFuture<Void> start() {
        return nettyServer.start();
    }

    @Override
    public CompletableFuture<Void> stop() {
        return nettyServer.stop();
    }

    @Override
    public void close() {
        nettyServer.close();
    }
}
