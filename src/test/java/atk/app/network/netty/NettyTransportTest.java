package atk.app.network.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import atk.app.network.MockRequest;
import atk.app.network.TcpRequest;
import atk.app.util.channel.BoundedChannel;
import atk.app.util.channel.Channel;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class NettyTransportTest {

    @Test
    void testTwoNettyTransports() throws ExecutionException, InterruptedException {
        Channel<TcpRequest> requestChannel2 = new BoundedChannel<>(10);

        var executorService = Executors.newCachedThreadPool();
        try (var nettyServer = new NettyServer(11, requestChannel2, executorService);) {
            nettyServer.start().get();

            var sendRequest = new MockRequest();
            var sendFeature = new NettyClient(executorService)
                    .send(sendRequest, new InetSocketAddress(11), Duration.ofSeconds(20));
            var receivedReq = nettyServer.getReceivedRequests().pull(Duration.ofMinutes(1));
            assertEquals(sendRequest, receivedReq.getRequest());
            sendFeature.cancel(true);
        }
    }
}
