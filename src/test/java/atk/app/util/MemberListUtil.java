package atk.app.util;

import atk.app.member.MemberList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MemberListUtil {

    public static MemberList createRandomList(int numberOfMembers) {
        var memberList = new MemberList(MemberStateUtil.aliveMember());
        List<MemberList.MemberState> memberStates = new ArrayList<>();
        for (int i = 0; i < numberOfMembers - 1; i++) {
            memberStates.add(MemberStateUtil.aliveMember());
        }
        memberList.update(memberStates.stream().collect(Collectors.toMap(m -> m.memberName, m -> m)));
        return memberList;
    }
}
