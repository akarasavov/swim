package atk.app.network.netty;

import static atk.app.util.channel.ConcurrencyUtil.waitDefaultTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import atk.app.network.MockRequest;
import atk.app.network.MockResponse;
import atk.app.network.TcpRequest;
import atk.app.util.channel.LimitedChannel;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class NettyServerTest {

    @Test
    void shouldSuccessfullySendAndReceiveMsg() throws Exception {
        int port = 8777;
        var channel = new LimitedChannel<TcpRequest>(10);
        var serverSocketAddress = new InetSocketAddress("127.0.0.1", port);
        var request = new MockRequest();
        var response = new MockResponse();
        var executorService = Executors.newCachedThreadPool();
        try (NettyServer server = new NettyServer(port, channel, executorService);
             NettyClient client = new NettyClient(serverSocketAddress, executorService, Duration.ofSeconds(10))) {
            waitDefaultTime(server.start());

            // when client connect to server
            waitDefaultTime(client.start());
            Executors.newSingleThreadExecutor().submit(() -> {
                var receivedRequest = channel.pull(Duration.ofMinutes(1));
                //server worker received the right request
                assertEquals(request, receivedRequest.getRequest());
                receivedRequest.getResponseHandler().complete(response);
            });

            // when client send a request
            var networkResponse = waitDefaultTime(client.sendMessage(request));

            //client worker received the right response
            assertEquals(response, networkResponse);
        }
    }

}
