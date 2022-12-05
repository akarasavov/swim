package atk.app.network.protocol;

import atk.app.member.MemberList;
import atk.app.network.NetworkResponse;
import java.util.List;
import java.util.Set;

/**
 *
 * @param memberStates - is a diff between the local state and pinged state
 * */
public record AckResponse(List<MemberList.MemberState> memberStates) implements NetworkResponse {
}
