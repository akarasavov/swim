package atk.app.network.protocol;

import atk.app.member.MemberList;
import atk.app.network.NetworkRequest;

public record FullStateSyncRequest(MemberList.MemberState memberState) implements NetworkRequest {

}
