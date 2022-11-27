package atk.app.network.protocol;

import atk.app.member.MemberList;
import atk.app.network.NetworkRequest;
import java.util.List;

public record PingRequest(List<MemberList.MemberState> memberStates) implements NetworkRequest {
    // TODO for udp protocol, there should be a source parameter
}
