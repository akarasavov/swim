package atk.app.network.netty;

import static atk.app.lifecycle.LifecycleStates.STARTED;
import atk.app.lifecycle.ThreadSafeLifecycle;
import atk.app.network.NetworkRequest;
import atk.app.network.NetworkResponse;
import atk.app.util.ConcurrencyUtil;
import atk.app.util.ExceptionUtil;
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
import java.io.IOException;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NettyClient implements NetworkClient {

    private final Duration establishConnectionDeadline;
    private final ExecutorService lifecycleExecutor;

    public NettyClient(ExecutorService lifecycleExecutor, Duration establishConnectionDeadline) {
        this.establishConnectionDeadline = establishConnectionDeadline;
        this.lifecycleExecutor = lifecycleExecutor;
    }

    @Override
    public CompletableFuture<NetworkResponse> send(NetworkRequest request, SocketAddress targetAddress) {
        // client is responsible for closing this resource
        var requestSender = new SingleRequestSender(lifecycleExecutor, targetAddress, establishConnectionDeadline);
        var result = requestSender.start()
                .thenCompose(unused -> requestSender.sendMessage(request));
        //stop netty client when future completes or it's closed from the client side
        result.whenComplete((networkResponse, throwable) -> requestSender.close());
        return result;
    }

    @Override
    public List<CompletableFuture<NetworkResponse>> send(NetworkRequest request, List<SocketAddress> targetAddresses) {
        return targetAddresses.stream().map(targetAddress -> send(request, targetAddress))
                .toList();
    }

    private static class SingleRequestSender extends ThreadSafeLifecycle {
        private final SocketAddress hostAddress;
        private final Bootstrap bootstrap;
        private final NioEventLoopGroup group;
        private final NettyClientHandler nettyClientHandler;
        private final ExecutorService nettyClientExecutor;
        private final Duration establishConnectionDeadline;

        private volatile Channel channel;

        public SingleRequestSender(ExecutorService lifecycleExecutor, SocketAddress hostAddress, Duration establishConnectionDeadline) {
            super(lifecycleExecutor);
            this.establishConnectionDeadline = establishConnectionDeadline;
            this.nettyClientExecutor = Executors.newSingleThreadExecutor();
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

        private CompletableFuture<NetworkResponse> sendMessage(NetworkRequest request) {
            return CompletableFuture.supplyAsync(() -> {
                verifyCurrentState(Set.of(STARTED));
                channel.writeAndFlush(request);
                logger.info("Send {} to {} ", request, hostAddress);
                //TODO - response timeout should be a configurable parameter
                var response = nettyClientHandler.getResponseHandler().pull(Duration.ofSeconds(10));
                if (response != null) {
                    return response;
                }
                throw new IllegalStateException("Request was interrupted, no response");
            }, nettyClientExecutor);
        }

        @Override
        protected void start0() {
            try {
                var channelFuture = bootstrap.connect(hostAddress).sync();
                channelFuture.get(establishConnectionDeadline.toMillis(), TimeUnit.MILLISECONDS);
                this.channel = channelFuture.channel();
                logger.debug("Successful bind to {}", hostAddress);
            } catch (Exception ex) {
                logger.info("Failed to connect {}", hostAddress, ex);
                throw new RuntimeException(ex);
            }
        }

        @Override
        protected void stop0() {
            try {
                if (channel == null) {
                    return;
                }
                channel.close().get();
                channel = null;
                logger.info("Close connection with {}", hostAddress);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void close0() {
            stop0();
            ExceptionUtil.ignoreThrownExceptions(() -> group.shutdownGracefully().get(), logger);
            ExceptionUtil.ignoreThrownExceptions(() -> nettyClientHandler.getResponseHandler().close(), logger);
            ExceptionUtil.ignoreThrownExceptions(() -> ConcurrencyUtil.shutdownExecutor(nettyClientExecutor), logger);
        }
    }
}
