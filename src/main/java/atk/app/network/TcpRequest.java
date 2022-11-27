package atk.app.network;

import java.util.concurrent.CompletableFuture;

public class TcpRequest {

    private final NetworkRequest networkRequest;
    private final CompletableFuture<NetworkResponse> responseHandler;

    public TcpRequest(NetworkRequest networkRequest) {
        this.networkRequest = networkRequest;
        this.responseHandler = new CompletableFuture<>();
    }

    public NetworkRequest getRequest() {
        return networkRequest;
    }

    public CompletableFuture<NetworkResponse> getResponseHandler() {
        return responseHandler;
    }


}
