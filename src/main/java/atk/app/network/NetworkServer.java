package atk.app.network;

import atk.app.lifecycle.Lifecycle;
import atk.app.util.channel.ReadableChannel;

public interface NetworkServer<T> extends Lifecycle<T> {
    ReadableChannel<TcpRequest> getReceivedRequests();
}
