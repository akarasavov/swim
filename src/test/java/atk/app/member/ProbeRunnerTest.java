package atk.app.member;

import static atk.app.util.ConcurrencyUtil.awaitForCompletion;
import atk.app.network.NetworkRequest;
import atk.app.network.NetworkResponse;
import atk.app.network.NetworkClient;
import atk.app.network.protocol.AckResponse;
import atk.app.network.protocol.IndirectPingRequest;
import atk.app.network.protocol.NetworkResponseHandler;
import atk.app.network.protocol.PingRequest;
import atk.app.util.ConcurrencyUtil;
import atk.app.util.MemberListUtil;
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

    private final SuspectTimers suspectTimers = Mockito.mock(SuspectTimers.class);
    private final NetworkResponseHandler responseHandler = Mockito.mock(NetworkResponseHandler.class);

    @Test
    void memberShouldBeSuspectedIfItCantBeProbedInTheExpectedDuration() throws ExecutionException, InterruptedException, TimeoutException {
        //given
        var lifecycleExecutor = Executors.newCachedThreadPool();
        var memberList = MemberListUtil.createRandomList(4);
        var networkClient = Mockito.mock(NetworkClient.class);
        //given any network request should fail
        Mockito.when(networkClient.send(Mockito.any(NetworkRequest.class), Mockito.any(SocketAddress.class), Mockito.any(Duration.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Wasn't able to contact member")));
        var probePeriod = Duration.ofSeconds(2);
        try (ProbeRunner probeRunner = new ProbeRunner(responseHandler, networkClient, memberList, suspectTimers,
                lifecycleExecutor, probePeriod, Duration.ofSeconds(1), 2)) {
            //when probe runner is started
            awaitForCompletion(probeRunner.start());
            //when one probe period is passed
            Thread.sleep(probePeriod.plusSeconds(1).toMillis());

            //then check that one member is marked as suspected
            Mockito.verify(suspectTimers, Mockito.atLeastOnce()).suspectMember(Mockito.any(MemberName.class));
        } finally {
            ConcurrencyUtil.shutdownExecutor(lifecycleExecutor);
        }
    }

    @Test
    void memberShouldBeNotSuspectedIfCanBeProbedByOneOfTheMembers() throws ExecutionException, InterruptedException, TimeoutException {
        //given
        var lifecycleExecutor = Executors.newCachedThreadPool();
        var memberList = MemberListUtil.createRandomList(4);
        var networkClient = Mockito.mock(NetworkClient.class);
        var suspectTimers = Mockito.mock(SuspectTimers.class);
        //given ping request fails
        Mockito.when(networkClient.send(Mockito.any(PingRequest.class), Mockito.any(SocketAddress.class), Mockito.any(Duration.class)))
                .thenReturn(failedResponse());
        //given all indirect request fails expect the last one
        Mockito.when(networkClient.send(Mockito.any(IndirectPingRequest.class), Mockito.any(List.class), Mockito.any(Duration.class)))
                .thenReturn(List.of(failedResponse(), CompletableFuture.supplyAsync(() -> new AckResponse(memberList.getMemberStates()))));
        var probePeriod = Duration.ofSeconds(2);
        try (ProbeRunner probeRunner = new ProbeRunner(responseHandler, networkClient, memberList, suspectTimers,
                lifecycleExecutor, probePeriod, Duration.ofSeconds(1), 2)) {
            //when probe runner is started
            awaitForCompletion(probeRunner.start());
            //when one probe period is passed
            Thread.sleep(probePeriod.plusSeconds(1).toMillis());

            //then check that one member is marked as suspected
            Mockito.verifyNoInteractions(suspectTimers);
        } finally {
            ConcurrencyUtil.shutdownExecutor(lifecycleExecutor);
        }
    }

    private CompletableFuture<NetworkResponse> failedResponse() {
        return CompletableFuture.failedFuture(new IllegalStateException("Wasn't able to contact member"));
    }
}
