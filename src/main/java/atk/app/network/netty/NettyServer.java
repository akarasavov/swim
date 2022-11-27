package atk.app.network.netty;

import static atk.app.util.FutureUtil.VOID;
import static atk.app.util.FutureUtil.toVoidFuture;
import atk.app.model.Lifecycle;
import atk.app.network.TcpRequest;
import atk.app.util.channel.WriteableChannel;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyServer implements Lifecycle<Void> {
    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
    private final ServerBootstrap bootstrap;
    private final List<EventLoopGroup> workerGroups;
    private final int port;
    private final ExecutorService nettyServerExecutor;
    private volatile Channel channel;

    public NettyServer(int port, WriteableChannel<TcpRequest> requestsChannel, ExecutorService executorService) {
        this.nettyServerExecutor = executorService;
        EventLoopGroup bossGroup = new NioEventLoopGroup(1, executorService);
        EventLoopGroup workerGroup = new NioEventLoopGroup(1, executorService);
        this.port = port;
        this.workerGroups = List.of(bossGroup, workerGroup);
        this.bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(
                                new ObjectEncoder(),
                                new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                new NettyServerHandler(requestsChannel));
                    }
                });
    }

    @Override
    public synchronized CompletableFuture<Void> start() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var channelFuture = bootstrap.bind(port).sync();
                channelFuture.get();
                this.channel = channelFuture.channel();
                logger.debug("Successfully bind to port {}", port);
                return VOID;
            } catch (Exception ex) {
                logger.error("Wasn't able to bind to port {}", port);
                throw new RuntimeException(ex);
            }
        }, nettyServerExecutor);

    }

    @Override
    public synchronized CompletableFuture<Void> stop() {
        return toVoidFuture(internalClose());
    }

    @Override
    public synchronized void close() {
        try {
            internalClose().get();
        } catch (InterruptedException | ExecutionException ignored) {
        }
    }

    private CompletableFuture<Void> internalClose() {
        return CompletableFuture.supplyAsync(() -> {
            if (channel == null) {
                throw new IllegalStateException("Netty server wasn't started");
            }
            channel.close();
            List<? extends Future<?>> futures = workerGroups.stream().map(EventExecutorGroup::shutdownGracefully).toList();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
            logger.debug("Successfully close connection");
            return null;
        }, nettyServerExecutor);
    }
}
