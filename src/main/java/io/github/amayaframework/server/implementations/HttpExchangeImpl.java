package io.github.amayaframework.server.implementations;

import com.github.romanqed.util.http.HeaderMap;
import com.github.romanqed.util.http.HttpCode;
import io.github.amayaframework.server.interfaces.HttpContext;
import io.github.amayaframework.server.interfaces.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

public class HttpExchangeImpl implements HttpExchange {
    private final ExchangeImpl impl;

    public HttpExchangeImpl(ExchangeImpl impl) {
        this.impl = impl;
    }

    public HeaderMap getRequestHeaders() {
        return impl.getRequestHeaders();
    }

    public HeaderMap getResponseHeaders() {
        return impl.getResponseHeaders();
    }

    public URI getRequestURI() {
        return impl.getRequestURI();
    }

    public String getRequestMethod() {
        return impl.getRequestMethod();
    }

    public HttpContext getHttpContext() {
        return impl.getHttpContext();
    }

    public void close() {
        impl.close();
    }

    public InputStream getRequestBody() {
        return impl.getRequestBody();
    }

    public HttpCode getResponseCode() {
        return impl.getResponseCode();
    }

    public OutputStream getResponseBody() {
        return impl.getResponseBody();
    }


    public void sendResponseHeaders(HttpCode code, long contentLen) throws IOException {
        impl.sendResponseHeaders(code, contentLen);
    }

    public InetSocketAddress getRemoteAddress() {
        return impl.getRemoteAddress();
    }

    public InetSocketAddress getLocalAddress() {
        return impl.getLocalAddress();
    }

    public String getProtocol() {
        return impl.getProtocol();
    }

    public Object getAttribute(String name) {
        return impl.getAttribute(name);
    }

    public void setAttribute(String name, Object value) {
        impl.setAttribute(name, value);
    }

    public void setStreams(InputStream inputStream, OutputStream outputStream) {
        impl.setStreams(inputStream, outputStream);
    }
}
