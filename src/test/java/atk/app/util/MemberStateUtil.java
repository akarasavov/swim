package atk.app.util;

import atk.app.member.MemberName;
import java.util.Random;

public class MemberStateUtil {
    private static final String alphaNumeric = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Random random = new Random();

    public static MemberName randomMemberName() {
        return new MemberName(randomString(10));
    }

    private static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            sb.append(alphaNumeric.charAt(random.nextInt(alphaNumeric.length())));
        return sb.toString();
    }
}
