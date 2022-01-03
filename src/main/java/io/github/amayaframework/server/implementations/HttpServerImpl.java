package io.github.amayaframework.server.implementations;

import io.github.amayaframework.server.interfaces.HttpContext;
import io.github.amayaframework.server.interfaces.HttpHandler;
import io.github.amayaframework.server.interfaces.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

public class HttpServerImpl implements HttpServer {
    protected ServerImpl server;

    protected HttpServerImpl() {
    }

    public HttpServerImpl(InetSocketAddress address, int backlog) throws IOException {
        server = new ServerImpl("http", address, backlog);
    }

    @Override
    public void bind(InetSocketAddress address, int backlog) throws IOException {
        server.bind(address, backlog);
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public Executor getExecutor() {
        return server.getExecutor();
    }

    @Override
    public void setExecutor(Executor executor) {
        server.setExecutor(executor);
    }

    @Override
    public void stop(int delay) {
        server.stop(delay);
    }

    @Override
    public HttpContext createContext(String path, HttpHandler handler) {
        return server.createContext(path, handler);
    }

    @Override
    public HttpContext createContext(String path) {
        return server.createContext(path);
    }

    @Override
    public void removeContext(String path) throws IllegalArgumentException {
        server.removeContext(path);
    }

    @Override
    public void removeContext(HttpContext context) {
        server.removeContext(context);
    }

    @Override
    public InetSocketAddress getAddress() {
        return server.getAddress();
    }
}
