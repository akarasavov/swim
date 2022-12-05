package atk.app.util;

import atk.app.member.MemberList;
import atk.app.member.MemberName;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;

public class MemberStateUtil {
    private static final String alphaNumeric = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Random random = new Random();

    public static MemberList.MemberState suspectMember() {
        return createMemberState(MemberList.MemberStateType.DEAD);
    }

    public static MemberList.MemberState deadMember() {
        return createMemberState(MemberList.MemberStateType.DEAD);
    }

    public static MemberList.MemberState aliveMember() {
        return createMemberState(MemberList.MemberStateType.ALIVE);
    }

    public static MemberList.MemberState updateIncarnationNumber(MemberList.MemberState memberState, int incarnationNumber) {
        return new MemberList.MemberState(memberState.memberName, aliveMember().bindAddress, incarnationNumber, memberState.stateType);
    }

    public static MemberList.MemberState createMemberState(MemberList.MemberStateType stateType) {
        return new MemberList.MemberState(randomMemberName(), new InetSocketAddress(0), 0, stateType);
    }

    public static MemberName randomMemberName() {
        return new MemberName(randomString(10));
    }

    public static MemberList.MemberState getMemberWithName(List<MemberList.MemberState> memberStates, MemberName name) {
        return memberStates.stream().filter(m -> m.memberName.equals(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("Can't find member with " + name));
    }

    private static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            sb.append(alphaNumeric.charAt(random.nextInt(alphaNumeric.length())));
        return sb.toString();
    }
}
