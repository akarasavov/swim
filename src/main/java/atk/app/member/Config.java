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
    public final Duration probePeriod;
    /**
     * Duration for which a suspected member needs to send an alive message, or otherwise it will be marked as dead
     */
    public final Duration suspectedMemberDeadline;
    public final Duration networkRequestMaximumDuration;
    public final int indirectPingTargets;

    public Config(MemberName memberName,
                  SocketAddress bindAddress,
                  Duration probePeriod,
                  Duration suspectedMemberDeadline,
                  Duration networkRequestMaximumDuration,
                  int indirectPingTargets) {
        this.memberName = memberName;
        this.bindAddress = bindAddress;
        this.probePeriod = probePeriod;
        this.suspectedMemberDeadline = suspectedMemberDeadline;
        this.networkRequestMaximumDuration = networkRequestMaximumDuration;
        this.indirectPingTargets = indirectPingTargets;
    }
}
