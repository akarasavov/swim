package atk.app.network.protocol;

import atk.app.member.MemberList;
import atk.app.network.NetworkResponse;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkResponseHandler {
    private static final Logger logger = LoggerFactory.getLogger(NetworkRequestHandler.class);
    private final MemberList memberList;

    public NetworkResponseHandler(MemberList memberList) {
        this.memberList = memberList;
    }

    public void processNetworkResponse(NetworkResponse response,
                                       Throwable throwable,
                                       String messageInCaseOfError) {
        if (response == null && throwable == null) {
            throw new IllegalArgumentException("Response or throwable should be not null");
        }
        if (throwable != null) {
            logger.error(messageInCaseOfError, throwable);
        } else {
            processNetworkResponse(response);
        }
    }

    public void processNetworkResponse(NetworkResponse response) {
        if (response instanceof FullStateSyncResponse) {
            processFullStateSyncResponse((FullStateSyncResponse) response);
        } else if (response instanceof AckResponse) {
            processAckResponse((AckResponse) response);
        } else {
            logger.error("Wasn't able to process {}", response);
            throw new IllegalArgumentException("Not supported response " + response);
        }
    }

    private void processFullStateSyncResponse(FullStateSyncResponse response) {
        var responseMap = response.memberStates().stream().collect(Collectors.toMap(k -> k.memberName, k -> k));
        memberList.update(responseMap);
    }

    private void processAckResponse(AckResponse ackResponse) {
        var remoteMembershipMap = ackResponse.memberStates().stream().collect(Collectors.toMap(k -> k.memberName, k -> k));
        memberList.update(remoteMembershipMap);
        logger.debug("Processed {}", ackResponse);
    }
}
