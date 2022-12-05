package atk.app.util;

import atk.app.member.Config;
import atk.app.member.Member;
import atk.app.member.MemberName;
import atk.app.network.netty.NettyClient;
import atk.app.network.netty.NettyServer;
import atk.app.util.channel.BoundedChannel;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MemberTestUtil implements Closeable {

    private final ExecutorService executor;
    private final ScheduledExecutorService scheduledExecutor;
    private final List<Closeable> closeables = new ArrayList<>();
    private int nextPort = 8777;

    public MemberTestUtil() {
        this.executor = Executors.newCachedThreadPool();
        this.scheduledExecutor = Executors.newScheduledThreadPool(10);
    }

    /**
     * No thread safe method
     */
    public TestMember createMember(String name) {
        var config = new Config(new MemberName(name), new InetSocketAddress("0.0.0.0", nextPort),
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                Duration.ofSeconds(3),
                2);
        var server = new NettyServer(nextPort, new BoundedChannel<>(10), executor);
        var client = new NettyClient(executor, Duration.ofSeconds(20));
        nextPort++;
        closeables.add(server);
        return new TestMember(config, new Member(config, executor, scheduledExecutor, server, client));
    }

    @Override
    public void close() {
        System.out.println("Close memberTestUtil");
        closeables.forEach(e -> {
            try {
                e.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        ConcurrencyUtil.shutdownExecutor(executor);
        ConcurrencyUtil.shutdownExecutor(scheduledExecutor);
    }

    public record TestMember(Config config, Member member) implements Closeable {
        @Override
        public void close() {
            member.close();
        }
    }
}
