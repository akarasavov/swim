package atk.app.network.protocol;

import atk.app.member.MemberList;
import atk.app.network.NetworkResponse;
import java.util.List;

public record FullStateSyncResponse(List<MemberList.MemberState> memberStates) implements NetworkResponse {
}
