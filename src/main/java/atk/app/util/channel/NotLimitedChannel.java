package atk.app.util.channel;

import java.util.concurrent.LinkedBlockingDeque;

public class NotLimitedChannel<T> extends AbstractChannel<T> {
    public NotLimitedChannel() {
        super(new LinkedBlockingDeque<>());
    }
}
