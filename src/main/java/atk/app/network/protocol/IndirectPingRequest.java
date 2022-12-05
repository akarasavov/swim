package atk.app.network.protocol;

import atk.app.member.MemberList;
import atk.app.network.NetworkRequest;
import java.net.SocketAddress;
import java.util.List;

//TODO - implement logic to react on indirect ping request
public record IndirectPingRequest(List<MemberList.MemberState> memberStates,
                                  SocketAddress probeTargetAddress) implements NetworkRequest {
}
