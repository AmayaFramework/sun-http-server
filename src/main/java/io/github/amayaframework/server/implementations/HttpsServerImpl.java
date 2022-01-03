package io.github.amayaframework.server.implementations;

import io.github.amayaframework.server.interfaces.HttpsServer;
import io.github.amayaframework.server.utils.HttpsConfigurator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;

public class HttpsServerImpl extends HttpServerImpl implements HttpsServer {
    public HttpsServerImpl(InetSocketAddress address, int backlog) throws IOException {
        super();
        server = new ServerImpl("https", address, backlog);
    }

    @Override
    public HttpsConfigurator getHttpsConfigurator() {
        return server.getHttpsConfigurator();
    }

    @Override
    public void setHttpsConfigurator(HttpsConfigurator config) {
        Objects.requireNonNull(config);
        server.setHttpsConfigurator(config);
    }
}
