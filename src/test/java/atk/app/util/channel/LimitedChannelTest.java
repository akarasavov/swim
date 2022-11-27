package atk.app.util.channel;

import atk.app.util.channel.LimitedChannel;
import org.junit.jupiter.api.Test;

class LimitedChannelTest {

    @Test
    void echo() {
        LimitedChannel<Integer> channel = new LimitedChannel<>(10);

    }
}
