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

import io.github.amayaframework.server.interfaces.HttpContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class HttpConnection {
    private final ServerImpl server;
    private HttpContext context;
    private SSLEngine engine;

    private SSLContext sslContext;
    private SSLStreams sslStreams;

    /* high level streams returned to application */
    private InputStream inputStream;

    /* low level stream that sits directly over channel */
    private InputStream rawInputStream;
    private OutputStream rawOutputStream;

    private SocketChannel channel;
    private SelectionKey selectionKey;
    private String protocol;
    private int remaining;
    private boolean closed = false;
    private volatile State state;

    private volatile long creationTime; // time this connection was created
    private volatile long responseStartedTime;
    private long time;

    public HttpConnection(ServerImpl server) {
        this.server = server;
    }

    public ServerImpl getServer() {
        return server;
    }

    public String toString() {
        String s = null;
        if (channel != null) {
            s = channel.toString();
        }
        return s;
    }

    public void setContext(HttpContext context) {
        this.context = context;
    }

    public State getState() {
        return state;
    }

    public void setState(State s) {
        state = s;
    }

    public void setSslContext(HttpContext context) {
        this.context = context;
    }

    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public void setSslStreams(SSLStreams sslStreams) {
        this.sslStreams = sslStreams;
    }

    public void setRawInputStream(InputStream rawInputStream) {
        this.rawInputStream = rawInputStream;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public void setChannel(SocketChannel c) {
        channel = c;
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!Objects.requireNonNull(channel).isOpen()) {
            // FIXME
            System.out.println("Channel already closed");
            return;
        }
        try {
            /* need to ensure temporary selectors are closed */
            if (rawInputStream != null) {
                rawInputStream.close();
            }
        } catch (IOException e) {
            // FIXME
            e.printStackTrace();
        }
        try {
            if (rawOutputStream != null) {
                rawOutputStream.close();
            }
        } catch (IOException e) {
            // FIXME
            e.printStackTrace();
        }
        if (sslStreams != null) {
            sslStreams.close();
        }
        try {
            channel.close();
        } catch (IOException e) {
            // FIXME
            e.printStackTrace();
        }
    }

    public int getRemaining() {
        return remaining;
    }

    /* remaining is the number of bytes left on the lowest level input stream
     * after the exchange is finished
     */
    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public OutputStream getRawOutputStream() {
        return rawOutputStream;
    }

    public void setRawOutputStream(OutputStream rawOutputStream) {
        this.rawOutputStream = rawOutputStream;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public SSLEngine getSSLEngine() {
        return engine;
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }

    public HttpContext getHttpContext() {
        return context;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getResponseStartedTime() {
        return responseStartedTime;
    }

    public void setResponseStartedTime(long responseStartedTime) {
        this.responseStartedTime = responseStartedTime;
    }

    public void setEngine(SSLEngine engine) {
        this.engine = engine;
    }

    public enum State {
        IDLE,
        REQUEST,
        RESPONSE
    }
}
