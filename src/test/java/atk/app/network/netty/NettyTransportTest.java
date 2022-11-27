package atk.app.network.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import atk.app.network.MockRequest;
import atk.app.network.TcpRequest;
import atk.app.util.channel.Channel;
import atk.app.util.channel.LimitedChannel;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class NettyTransportTest {


    @Test
    void testTwoNettyTransports() throws ExecutionException, InterruptedException {
        Channel<TcpRequest> requestChannel1 = new LimitedChannel<>(10);
        Channel<TcpRequest> requestChannel2 = new LimitedChannel<>(10);

        var executorService = Executors.newCachedThreadPool();
        var nettyTransport1 = new NettyTransport(10, requestChannel1, Duration.ofMinutes(1), executorService);
        var nettyTransport2 = new NettyTransport(11, requestChannel2, Duration.ofMinutes(1), executorService);

        nettyTransport1.start().get();
        nettyTransport2.start().get();

        var sendRequest = new MockRequest();
        nettyTransport1.send(sendRequest, new InetSocketAddress(11));
        var receivedReq = nettyTransport2.getReceivedRequests().pull(Duration.ofMinutes(1));
        assertEquals(sendRequest, receivedReq.getRequest());

    }
}
