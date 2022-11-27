package atk.app.util;

import static atk.app.util.channel.ConcurrencyUtil.shutdownExecutor;
import atk.app.member.Config;
import atk.app.member.Member;
import atk.app.member.MemberName;
import atk.app.network.netty.NettyTransport;
import atk.app.util.channel.ConcurrencyUtil;
import atk.app.util.channel.LimitedChannel;
import java.io.Closeable;
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
    private final List<ExecutorService> executors = new ArrayList<>();
    private int nextPort = 8777;

    public MemberTestUtil() {
        this.executor = Executors.newCachedThreadPool();
        this.scheduledExecutor = Executors.newScheduledThreadPool(1);
        executors.add(executor);
        executors.add(scheduledExecutor);
    }

    public TestMember createMember(String name) {
        //TODO - dead timeout can't be equal to period timeout
        var config = new Config(new MemberName(name), new InetSocketAddress("0.0.0.0", nextPort), Duration.ofSeconds(10), Duration.ofSeconds(10));
        var transport = new NettyTransport(nextPort, new LimitedChannel<>(10), Duration.ofMinutes(1), executor);
        nextPort++;
        return new TestMember(config, new Member(config, executor, scheduledExecutor, transport));
    }

    @Override
    public void close() {
        executors.forEach(ConcurrencyUtil::shutdownExecutor);
    }

    public record TestMember(Config config, Member member) implements Closeable {
        @Override
        public void close() {
            member.close();
        }
    }
}
