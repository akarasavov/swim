package atk.app.util.channel;

import java.io.Closeable;

public interface Channel<T> extends ReadableChannel<T>, WriteableChannel<T>, Closeable {
}
