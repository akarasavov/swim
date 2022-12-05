package atk.app.member;

import static atk.app.util.ConcurrencyUtil.*;
import atk.app.network.NetworkRequest;
import atk.app.network.NetworkResponse;
import atk.app.network.netty.NetworkClient;
import atk.app.network.protocol.AckResponse;
import atk.app.network.protocol.IndirectPingRequest;
import atk.app.network.protocol.PingRequest;
import atk.app.util.MemberListUtil;
import atk.app.util.ConcurrencyUtil;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProbeRunnerTest {

    @Test
    void memberShouldBeSuspectedIfItCantBeProbedInTheExpectedDuration() throws ExecutionException, InterruptedException, TimeoutException {
        //given
        var lifecycleExecutor = Executors.newCachedThreadPool();
        var scheduledExecutor = Executors.newScheduledThreadPool(2);
        var memberList = MemberListUtil.createRandomList(4);
        var networkClient = Mockito.mock(NetworkClient.class);
        var suspectTimers = Mockito.mock(SuspectTimers.class);
        Mockito.when(networkClient.send(Mockito.any(NetworkRequest.class), Mockito.any(SocketAddress.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Wasn't able to contact member")));
        var probePeriod = Duration.ofSeconds(2);
        try (ProbeRunner probeRunner = new ProbeRunner(networkClient, memberList, suspectTimers, scheduledExecutor,
                lifecycleExecutor, probePeriod, Duration.ofSeconds(1), 2)) {
            //when probe runner is started
            awaitForCompletion(probeRunner.start());
            //when one probe period is passed
            Thread.sleep(probePeriod.plusSeconds(1).toMillis());

            //then check that one member is marked as suspected
            Mockito.verify(suspectTimers, Mockito.atLeastOnce()).suspectMember(Mockito.any(MemberName.class));
        } finally {
            ConcurrencyUtil.shutdownExecutor(lifecycleExecutor);
            ConcurrencyUtil.shutdownExecutor(scheduledExecutor);
        }
    }

    @Test
    void memberShouldBeNotSuspectedIfCanBeProbedByOneOfTheMembers() throws ExecutionException, InterruptedException, TimeoutException {
        //given
        var lifecycleExecutor = Executors.newCachedThreadPool();
        var scheduledExecutor = Executors.newScheduledThreadPool(2);
        var memberList = MemberListUtil.createRandomList(4);
        var networkClient = Mockito.mock(NetworkClient.class);
        var suspectTimers = Mockito.mock(SuspectTimers.class);
        //given ping request fails
        Mockito.when(networkClient.send(Mockito.any(PingRequest.class), Mockito.any(SocketAddress.class)))
                .thenReturn(failedResponse());
        //given all indirect request fails expect the last one
        Mockito.when(networkClient.send(Mockito.any(IndirectPingRequest.class), Mockito.any(List.class)))
                .thenReturn(List.of(failedResponse(), CompletableFuture.supplyAsync(() -> new AckResponse(memberList.getMemberStates()))));
        var probePeriod = Duration.ofSeconds(2);
        try (ProbeRunner probeRunner = new ProbeRunner(networkClient, memberList, suspectTimers, scheduledExecutor,
                lifecycleExecutor, probePeriod, Duration.ofSeconds(1), 2)) {
            //when probe runner is started
            awaitForCompletion(probeRunner.start());
            //when one probe period is passed
            Thread.sleep(probePeriod.plusSeconds(1).toMillis());

            //then check that one member is marked as suspected
            Mockito.verifyNoInteractions(suspectTimers);
        } finally {
            ConcurrencyUtil.shutdownExecutor(lifecycleExecutor);
            ConcurrencyUtil.shutdownExecutor(scheduledExecutor);
        }
    }

    private CompletableFuture<NetworkResponse> failedResponse() {
        return CompletableFuture.failedFuture(new IllegalStateException("Wasn't able to contact member"));
    }
}
