package atk.app.network.netty;

import static atk.app.util.FutureUtil.VOID;
import atk.app.model.Lifecycle;
import atk.app.network.NetworkRequest;
import atk.app.network.NetworkResponse;
import atk.app.network.TcpRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyClient implements Lifecycle<Void> {
    private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);
    private final SocketAddress hostAddress;
    private final Bootstrap bootstrap;
    private final NioEventLoopGroup group;
    private final NettyClientHandler nettyClientHandler;
    private final ExecutorService nettyClientExecutor;
    private final Duration establishConnectionDeadline;

    private volatile Channel channel;

    public NettyClient(SocketAddress hostAddress, ExecutorService nettyClientExecutor,
                       Duration establishConnectionDeadline) {
        this.establishConnectionDeadline = establishConnectionDeadline;
        this.nettyClientExecutor = nettyClientExecutor;
        this.hostAddress = hostAddress;
        this.group = new NioEventLoopGroup(1);
        this.bootstrap = new Bootstrap();
        this.nettyClientHandler = new NettyClientHandler();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(
                                new ObjectEncoder(),
                                new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                nettyClientHandler
                        );
                    }
                });
    }

    public CompletableFuture<NetworkResponse> sendMessage(NetworkRequest request) {
        channel.writeAndFlush(request);
        logger.info("Send {} to {} ", request, hostAddress);
        return CompletableFuture.supplyAsync(() -> {
            while (true) {
                var response = nettyClientHandler.getResponseHandler().pull(Duration.ofMinutes(1));
                if (response != null) {
                    return response;
                }
            }
        }, nettyClientExecutor);
    }

    @Override
    public synchronized CompletableFuture<Void> start() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var channelFuture = bootstrap.connect(hostAddress).sync();
                //TODO - replace this with config
                channelFuture.get(establishConnectionDeadline.toMillis(), TimeUnit.MILLISECONDS);
                this.channel = channelFuture.channel();
                logger.debug("Successful bind to {}", hostAddress);
                return VOID;
            } catch (Exception ex) {
                logger.info("Failed to connect {}", hostAddress, ex);
                throw new RuntimeException(ex);
            }

        }, nettyClientExecutor);
    }

    @Override
    public synchronized CompletableFuture<Void> stop() {
        if (channel == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(String.format("Client with hostAddress %s wasn't started", hostAddress)));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                channel.close().get();
                channel = null;
                logger.info("Close connection with {}", hostAddress);
                return VOID;
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Wasn't able to close connection successfully", e);
                throw new RuntimeException(e);
            }
        }, nettyClientExecutor);
    }

    @Override
    public void close() {
        try {
            //TODO - replace this with config
            stop().get();
            group.shutdownGracefully().get();
        } catch (InterruptedException | ExecutionException ignored) {
        }
    }
}
