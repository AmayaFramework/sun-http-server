package io.github.amayaframework.server;

import io.github.amayaframework.server.implementations.HttpServerImpl;
import io.github.amayaframework.server.implementations.HttpsServerImpl;
import io.github.amayaframework.server.interfaces.HttpServer;
import io.github.amayaframework.server.interfaces.HttpsServer;
import io.github.amayaframework.server.utils.HttpsConfigurator;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Servers {
    /**
     * Create a <code>HttpsServer</code> instance which will bind to the
     * specified {@link java.net.InetSocketAddress} (IP address and port number)
     * <p>
     * A maximum backlog can also be specified. This is the maximum number of
     * queued incoming connections to allow on the listening socket.
     * Queued TCP connections exceeding this limit may be rejected by the TCP implementation.
     * The server must have a HttpsConfigurator
     * established with {@link HttpsServer#setHttpsConfigurator(HttpsConfigurator)}
     *
     * @param address the address to listen on, if <code>null</code> then bind() must be called
     *                to set the address
     * @param backlog the socket backlog. If this value is less than or equal to zero,
     *                then a system default value is used.
     * @return created https server
     * @throws IOException if server creation will be failed
     */
    public static HttpsServer httpsServer(InetSocketAddress address, int backlog) throws IOException {
        return new HttpsServerImpl(address, backlog);
    }

    /**
     * Creates a HttpsServer instance which is initially not bound to any local address/port.
     * The server must be bound using {@link HttpsServer#bind(InetSocketAddress, int)} before it can be used.
     * The server must also have a HttpsConfigurator established
     * with {@link HttpsServer#setHttpsConfigurator(HttpsConfigurator)}
     *
     * @return created https server
     * @throws IOException if server creation will be failed
     */
    public static HttpsServer httpsServer() throws IOException {
        return httpsServer(null, 0);
    }

    /**
     * Create a <code>HttpServer</code> instance which will bind to the
     * specified {@link java.net.InetSocketAddress} (IP address and port number)
     * <p>
     * A maximum backlog can also be specified. This is the maximum number of
     * queued incoming connections to allow on the listening socket.
     * Queued TCP connections exceeding this limit may be rejected by the TCP implementation.
     *
     * @param address the address to listen on, if <code>null</code> then bind() must be called
     *                to set the address
     * @param backlog the socket backlog. If this value is less than or equal to zero,
     *                then a system default value is used.
     * @return created http server
     * @throws IOException if server creation will be failed
     */
    public static HttpServer httpServer(InetSocketAddress address, int backlog) throws IOException {
        return new HttpServerImpl(address, backlog);
    }

    /**
     * Creates a HttpServer instance which is initially not bound to any local address/port.
     * The server must be bound using {@link HttpServer#bind(InetSocketAddress, int)} before it can be used.
     *
     * @return created http server
     * @throws IOException if server creation will be failed
     */
    public static HttpServer httpServer() throws IOException {
        return httpServer(null, 0);
    }
}
