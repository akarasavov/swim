package atk.app.member;

import java.net.SocketAddress;
import java.time.Duration;

public class Config {

    public final MemberName memberName;
    public final SocketAddress bindAddress; // server socket address
    /**
     * Maximum period for a which a member waits to receive an ack from ping or indirect ping.
     * If member doesn't receive an ack from a target member it will be marked as suspicous
     */
    public final Duration protocolPeriod;
    //TODO - explain what is the purpose of this timeout. Think about better name
    public final Duration deadMemberTimeout;

    public Config(MemberName memberName, SocketAddress bindAddress, Duration protocolPeriod, Duration deadMemberTimeout) {
        this.memberName = memberName;
        this.bindAddress = bindAddress;
        this.protocolPeriod = protocolPeriod;
        this.deadMemberTimeout = deadMemberTimeout;
    }
}
