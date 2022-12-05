package atk.app.network.netty;

import atk.app.lifecycle.ThreadSafeLifecycle;
import atk.app.network.NetworkServer;
import atk.app.network.TcpRequest;
import atk.app.util.ExceptionUtil;
import atk.app.util.channel.ReadableChannel;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyServer extends ThreadSafeLifecycle implements NetworkServer<Void> {
    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
    private final ServerBootstrap bootstrap;
    private final List<EventLoopGroup> workerGroups;
    private final int port;
    private final ReadableChannel<TcpRequest> readableChannel;
    private volatile Channel networkChannel;

    public NettyServer(int port, atk.app.util.channel.Channel<TcpRequest> channel, ExecutorService lifecycleExecutor) {
        super(lifecycleExecutor);
        EventLoopGroup bossGroup = new NioEventLoopGroup(1, lifecycleExecutor);
        EventLoopGroup workerGroup = new NioEventLoopGroup(1, lifecycleExecutor);
        this.port = port;
        this.workerGroups = List.of(bossGroup, workerGroup);
        this.bootstrap = new ServerBootstrap();
        this.readableChannel = channel;
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
                                new NettyServerHandler(channel));
                    }
                });
    }

    @Override
    protected void start0() {
        try {
            var channelFuture = bootstrap.bind(port).sync();
            channelFuture.get();
            this.networkChannel = channelFuture.channel();
            logger.debug("Successfully bind to port {}", port);
        } catch (Exception ex) {
            logger.error("Wasn't able to bind to port {}", port, ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    protected void stop0() {
        internalClose();
    }

    @Override
    protected void close0() {
        ExceptionUtil.ignoreThrownExceptions(readableChannel::close, logger);
        internalClose();
    }

    @Override
    public ReadableChannel<TcpRequest> getReceivedRequests() {
        return readableChannel;
    }

    private void internalClose() {
        if (networkChannel == null) {
            return;
        }
        networkChannel.close();
        networkChannel = null;
        List<? extends Future<?>> futures = workerGroups.stream().map(EventExecutorGroup::shutdownGracefully).toList();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        logger.debug("Successfully close connection");
    }
}
