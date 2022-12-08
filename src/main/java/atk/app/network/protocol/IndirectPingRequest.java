package atk.app.network.protocol;

import atk.app.member.MemberList;
import atk.app.network.NetworkRequest;
import java.net.SocketAddress;
import java.util.List;

public record IndirectPingRequest(List<MemberList.MemberState> memberStates,
                                  SocketAddress probeTargetAddress) implements NetworkRequest {
}
