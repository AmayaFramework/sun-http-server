/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package io.github.amayaframework.server.implementations;

import io.github.amayaframework.server.events.WriteFinishedEvent;
import io.github.amayaframework.server.interfaces.HttpContext;
import io.github.amayaframework.server.interfaces.HttpExchange;
import io.github.amayaframework.server.streams.*;
import io.github.amayaframework.server.utils.Formats;
import io.github.amayaframework.server.utils.HeaderMap;
import io.github.amayaframework.server.utils.HttpCode;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.*;

public class ExchangeImpl implements HttpExchange {
    private static final String HEAD = "HEAD";
    private final HeaderMap requestHeaders;
    private final HeaderMap responseHeaders;
    private final Request request;
    private final String method;
    private final URI uri;
    private final HttpConnection connection;
    private final long requestContentLength;
    private final InputStream requestInputStream;
    private final OutputStream requestOutputStream;
    private boolean writeFinished;
    private boolean closed;
    private boolean close;
    private boolean http10;
    private InputStream inputStream;
    private OutputStream outputStream;
    private LeftOverInputStream origInputStream;
    private PlaceholderOutputStream origOutputStream;
    private boolean sentHeaders;
    private Map<String, Object> attributes;
    private HttpCode code;
    private byte[] responseBuffer = new byte[128];

    public ExchangeImpl(String method, URI uri, Request request, long length, HttpConnection connection) {
        this.request = request;
        this.requestHeaders = request.headers;
        this.responseHeaders = new HeaderMap();
        this.method = method;
        this.uri = uri;
        this.connection = connection;
        this.requestContentLength = length;
        this.requestOutputStream = request.outputStream();
        this.requestInputStream = request.inputStream();
        connection.getServer().startExchange();
    }

    public SSLSession getSSLSession() {
        SSLEngine e = connection.getSSLEngine();
        if (e == null) {
            return null;
        }
        return e.getSession();
    }

    public ServerImpl getServer() {
        return connection.getServer();
    }

    public HttpConnection getConnection() {
        return connection;
    }

    @Override
    public HeaderMap getRequestHeaders() {
        return HeaderMap.unmodifiableHeaderMap(requestHeaders);
    }

    @Override
    public HeaderMap getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public URI getRequestURI() {
        return uri;
    }

    @Override
    public String getRequestMethod() {
        return method;
    }

    @Override
    public HttpContext getHttpContext() {
        return connection.getHttpContext();
    }

    private boolean isHeadRequest() {
        return HEAD.equals(method);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        /* close the underlying connection if,
         * a) the streams not set up yet, no response can be sent, or
         * b) if the wrapper output stream is not set up, or
         * c) if the close of the input/output stream fails
         */
        try {
            if (origInputStream == null || outputStream == null) {
                connection.close();
                return;
            }
            if (!origOutputStream.isWrapped()) {
                connection.close();
                return;
            }
            if (!origInputStream.isClosed()) {
                origOutputStream.close();
            }
            outputStream.close();
        } catch (IOException e) {
            connection.close();
        }
    }

    @Override
    public InputStream getRequestBody() {
        if (inputStream != null) {
            return inputStream;
        }
        if (requestContentLength == -1L) {
            origInputStream = new ChunkedInputStream(this, requestInputStream);
        } else {
            origInputStream = new FixedLengthInputStream(this, requestInputStream, requestContentLength);
        }
        inputStream = origInputStream;
        return inputStream;
    }

    public LeftOverInputStream getOriginalInputStream() {
        return origInputStream;
    }

    @Override
    public OutputStream getResponseBody() {
        if (outputStream == null) {
            origOutputStream = new PlaceholderOutputStream(null);
            outputStream = origOutputStream;
        }
        return outputStream;
    }

    public PlaceholderOutputStream getPlaceholderResponseBody() {
        getResponseBody();
        return origOutputStream;
    }

    private byte[] bytes(String s, int extra) {
        int length = s.length();
        if (length + extra > responseBuffer.length) {
            int diff = length + extra - responseBuffer.length;
            responseBuffer = new byte[2 * (responseBuffer.length + diff)];
        }
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) {
            responseBuffer[i] = (byte) c[i];
        }
        return responseBuffer;
    }

    private void write(HeaderMap map, OutputStream os) throws IOException {
        Set<Map.Entry<String, List<String>>> entries = map.entrySet();
        for (Map.Entry<String, List<String>> entry : entries) {
            String key = entry.getKey();
            byte[] buf;
            List<String> values = entry.getValue();
            for (String val : values) {
                int i = key.length();
                buf = bytes(key, 2);
                buf[i++] = ':';
                buf[i++] = ' ';
                os.write(buf, 0, i);
                buf = bytes(val, 2);
                i = val.length();
                buf[i++] = '\r';
                buf[i++] = '\n';
                os.write(buf, 0, i);
            }
        }
        os.write('\r');
        os.write('\n');
    }

    @Override
    public void sendResponseHeaders(HttpCode code, long responseLength) throws IOException {
        if (sentHeaders) {
            throw new IOException("headers already sent");
        }
        this.code = Objects.requireNonNull(code);
        String statusLine = "HTTP/1.1 " + code.getCode() + " " + code.getMessage() + "\r\n";
        OutputStream tempOut = new BufferedOutputStream(requestOutputStream);
        PlaceholderOutputStream o = getPlaceholderResponseBody();
        tempOut.write(bytes(statusLine, 0), 0, statusLine.length());
        boolean noContentToSend = false; // assume there is content
        responseHeaders.set("Date", Formats.formatDate(new Date()));

        /* check for response type that is not allowed to send a body */

        if ((code.getCode() >= 100 && code.getCode() < 200) /* informational */
                || (code.getCode() == 204)           /* no content */
                || (code.getCode() == 304))          /* not modified */ {
            responseLength = -1;
        }

        if (isHeadRequest()) {
            /* HEAD requests should not set a content length by passing it
             * through this API, but should instead manually set the required
             * headers.*/
            noContentToSend = true;
        } else { /* not a HEAD request */
            if (responseLength == 0) {
                if (http10) {
                    o.setWrappedStream(new UndefLengthOutputStream(this, requestOutputStream));
                } else {
                    responseHeaders.set("Transfer-encoding", "chunked");
                    o.setWrappedStream(new ChunkedOutputStream(this, requestOutputStream));
                }
            } else {
                if (responseLength == -1) {
                    noContentToSend = true;
                    responseLength = 0;
                }
                responseHeaders.set("Content-length", Long.toString(responseLength));
                o.setWrappedStream(new FixedLengthOutputStream(this, requestOutputStream, responseLength));
            }
        }
        write(responseHeaders, tempOut);
        tempOut.flush();
        sentHeaders = true;
        if (noContentToSend) {
            WriteFinishedEvent e = new WriteFinishedEvent(this);
            connection.getServer().addEvent(e);
            closed = true;
        }
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        Socket socket = connection.getChannel().socket();
        InetAddress inetAddress = socket.getInetAddress();
        int port = socket.getPort();
        return new InetSocketAddress(inetAddress, port);
    }

    @Override
    public HttpCode getResponseCode() {
        return code;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        Socket socket = connection.getChannel().socket();
        InetAddress inetAddress = socket.getLocalAddress();
        int port = socket.getLocalPort();
        return new InetSocketAddress(inetAddress, port);
    }

    @Override
    public String getProtocol() {
        String requestLine = request.requestLine();
        int index = requestLine.lastIndexOf(' ');
        return requestLine.substring(index + 1);
    }

    @Override
    public Object getAttribute(String name) {
        if (name == null) {
            throw new NullPointerException("null name parameter");
        }
        if (attributes == null) {
            attributes = getHttpContext().getAttributes();
        }
        return attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        if (name == null) {
            throw new NullPointerException("null name parameter");
        }
        if (attributes == null) {
            attributes = getHttpContext().getAttributes();
        }
        attributes.put(name, value);
    }

    @Override
    public void setStreams(InputStream inputStream, OutputStream outputStream) {
        if (inputStream != null) {
            this.inputStream = inputStream;
        }
        if (outputStream != null) {
            this.outputStream = outputStream;
        }
    }

    public boolean isWriteFinished() {
        return writeFinished;
    }

    public void setWriteFinished(boolean writeFinished) {
        this.writeFinished = writeFinished;
    }

    public boolean isHttp10() {
        return http10;
    }

    public void setHttp10(boolean http10) {
        this.http10 = http10;
    }

    public boolean isClose() {
        return close;
    }

    public void setClose(boolean close) {
        this.close = close;
    }
}
