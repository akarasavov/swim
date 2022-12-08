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

public class MemberTestUtil implements Closeable {

    private final ExecutorService executor;
    private final List<Closeable> closeables = new ArrayList<>();
    private int nextPort = 8777;

    public MemberTestUtil() {
        this.executor = Executors.newCachedThreadPool();
    }

    public TestMember createMember(String name) {
        return createMember(name, Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(3));
    }

    public TestMember createMember(String name, Duration probePeriod, Duration networkRequestTimeout) {
        return createMember(name, probePeriod, Duration.ofSeconds(10), networkRequestTimeout);
    }

    /**
     * No thread safe method
     */
    public TestMember createMember(String name, Duration probePeriod, Duration suspectMemberDeadline, Duration networkRequestTimeout) {
        var config = new Config(new MemberName(name), new InetSocketAddress("0.0.0.0", nextPort),
                probePeriod,
                suspectMemberDeadline,
                networkRequestTimeout,
                2);
        var server = new NettyServer(nextPort, new BoundedChannel<>(10), executor);
        var client = new NettyClient(executor);
        nextPort++;
        closeables.add(server);
        return new TestMember(config, new Member(config, executor, server, client));
    }

    @Override
    public void close() {
        closeables.forEach(e -> {
            try {
                e.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        ConcurrencyUtil.shutdownExecutor(executor);
    }

    public record TestMember(Config config, Member member) implements Closeable {
        @Override
        public void close() {
            member.close();
        }
    }
}
