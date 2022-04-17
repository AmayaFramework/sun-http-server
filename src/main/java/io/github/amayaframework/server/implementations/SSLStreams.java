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

import io.github.amayaframework.server.interfaces.HttpsParameters;
import io.github.amayaframework.server.utils.HttpsConfigurator;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SSLStreams {
    private final SSLEngine engine;
    private final EngineWrapper wrapper;
    /* held by thread doing the hand-shake on this connection */
    private final Lock handshaking = new ReentrantLock();
    private OutputStream outputStream;
    private InputStream inputStream;
    private int appBufSize;
    private int packetBufSize;

    public SSLStreams(HttpsConfigurator configurator, SSLContext sslContext, SocketChannel chan) {
        InetSocketAddress address = (InetSocketAddress) chan.socket().getRemoteSocketAddress();
        engine = sslContext.createSSLEngine(address.getHostName(), address.getPort());
        engine.setUseClientMode(false);
        configureEngine(configurator, address);
        wrapper = new EngineWrapper(chan, engine);
    }

    private void configureEngine(HttpsConfigurator configurator, InetSocketAddress address) {
        if (configurator != null) {
            Parameters params = new Parameters(configurator, address);
            configurator.configure(params);
            SSLParameters sslParams = params.getSSLParameters();
            if (sslParams != null) {
                engine.setSSLParameters(sslParams);
            } else {
                if (params.getCipherSuites() != null) {
                    try {
                        engine.setEnabledCipherSuites(
                                params.getCipherSuites()
                        );
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                }
                engine.setNeedClientAuth(params.getNeedClientAuth());
                engine.setWantClientAuth(params.getWantClientAuth());
                if (params.getProtocols() != null) {
                    try {
                        engine.setEnabledProtocols(
                                params.getProtocols()
                        );
                    } catch (IllegalArgumentException e) { /* LOG */}
                }
            }
        }
    }

    /**
     * cleanup resources allocated inside this object
     */
    public void close() {
        wrapper.close();
    }

    /**
     * @return the SSL InputStream
     */
    public InputStream getInputStream() {
        if (inputStream == null) {
            inputStream = new InputStream();
        }
        return inputStream;
    }

    /**
     * @return the SSL OutputStream
     */
    public OutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = new OutputStream();
        }
        return outputStream;
    }

    public SSLEngine getSSLEngine() {
        return engine;
    }

    /**
     * request the engine to repeat the handshake on this session
     * the handshake must be driven by reads/writes on the streams
     * Normally, not necessary to call this.
     *
     * @throws SSLException if begin handshake will be failed
     */
    public void beginHandshake() throws SSLException {
        engine.beginHandshake();
    }

    private ByteBuffer allocate(BufType type) {
        return allocate(type, -1);
    }

    private ByteBuffer allocate(BufType type, int len) {
        synchronized (this) {
            int size;
            if (type == BufType.PACKET) {
                if (packetBufSize == 0) {
                    SSLSession sess = engine.getSession();
                    packetBufSize = sess.getPacketBufferSize();
                }
                if (len > packetBufSize) {
                    packetBufSize = len;
                }
                size = packetBufSize;
            } else {
                if (appBufSize == 0) {
                    SSLSession sess = engine.getSession();
                    appBufSize = sess.getApplicationBufferSize();
                }
                if (len > appBufSize) {
                    appBufSize = len;
                }
                size = appBufSize;
            }
            return ByteBuffer.allocate(size);
        }
    }

    /* reallocates the buffer by :-
     * 1. creating a new buffer double the size of the old one
     * 2. putting the contents of the old buffer into the new one
     * 3. set xx_buf_size to the new size if it was smaller than new size
     *
     * flip is set to true if the old buffer needs to be flipped
     * before it is copied.
     */
    private ByteBuffer realloc(ByteBuffer b, boolean flip, BufType type) {
        synchronized (this) {
            int nSize = 2 * b.capacity();
            ByteBuffer n = allocate(type, nSize);
            if (flip) {
                b.flip();
            }
            n.put(b);
            b = n;
        }
        return b;
    }

    /**
     * send the data in the given ByteBuffer. If a handshake is needed
     * then this is handled within this method. When this call returns,
     * all the given user data has been sent and any handshake has been
     * completed. Caller should check if engine has been closed.
     */
    private int checkResult(SSLEngineResult result) throws IOException {
        if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
            doClosure();
            return 1;
        }
        SSLEngineResult.HandshakeStatus handshakeStatus = result.getHandshakeStatus();
        if (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
                handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            doHandshake(handshakeStatus);
        }
        return 0;
    }

    public WrapperResult sendData(ByteBuffer src) throws IOException {
        WrapperResult wrapperResult = null;
        while (src.remaining() > 0) {
            wrapperResult = wrapper.wrapAndSend(src);
            if (checkResult(wrapperResult.result) != 0) {
                return wrapperResult;
            }
        }
        return wrapperResult;
    }

    /**
     * read data through the engine into the given ByteBuffer. If the
     * given buffer was not large enough, a new one is allocated
     * and returned. This call handles handshaking automatically.
     * Caller should check if engine has been closed.
     *
     * @param dst {@link ByteBuffer} to write data
     * @return {@link WrapperResult}
     * @throws IOException if recv will be failed
     */
    public WrapperResult recvData(ByteBuffer dst) throws IOException {
        /* we wait until some user data arrives */
        WrapperResult wrapperResult = null;
        while (dst.position() == 0) {
            wrapperResult = wrapper.recvAndUnwrap(dst);
            dst = (wrapperResult.buf != dst) ? wrapperResult.buf : dst;
            if (checkResult(wrapperResult.result) != 0) {
                return wrapperResult;
            }
        }
        dst.flip();
        return wrapperResult;
    }

    /* we've received a close notify. Need to call wrap to send
     * the response
     */
    void doClosure() throws IOException {
        try {
            handshaking.lock();
            ByteBuffer tmp = allocate(BufType.APPLICATION);
            WrapperResult r;
            SSLEngineResult.Status st;
            SSLEngineResult.HandshakeStatus hs;
            do {
                tmp.clear();
                tmp.flip();
                r = wrapper.wrapAndSendX(tmp, true);
                hs = r.result.getHandshakeStatus();
                st = r.result.getStatus();
            } while (st != SSLEngineResult.Status.CLOSED &&
                    !(st == SSLEngineResult.Status.OK && hs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING));
        } finally {
            handshaking.unlock();
        }
    }

    /* do the (complete) handshake after acquiring the handshake lock.
     * If two threads call this at the same time, then we depend
     * on the wrapper methods being idempotent. eg. if wrapAndSend()
     * is called with no data to send then there must be no problem
     */
    @SuppressWarnings("fallthrough")
    private void doHandshake(SSLEngineResult.HandshakeStatus handshakeStatus) throws IOException {
        try {
            handshaking.lock();
            ByteBuffer tmp = allocate(BufType.APPLICATION);
            while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
                    handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                WrapperResult r = null;
                switch (handshakeStatus) {
                    case NEED_TASK:
                        Runnable task;
                        while ((task = engine.getDelegatedTask()) != null) {
                            /* run in current thread, because we are already
                             * running an external Executor
                             */
                            task.run();
                        }
                        /* fall through - call wrap again */
                    case NEED_WRAP:
                        tmp.clear();
                        tmp.flip();
                        r = wrapper.wrapAndSend(tmp);
                        break;

                    case NEED_UNWRAP:
                        tmp.clear();
                        r = wrapper.recvAndUnwrap(tmp);
                        if (r.buf != tmp) {
                            tmp = r.buf;
                        }
                        break;
                }
                handshakeStatus = r.result.getHandshakeStatus();
            }
        } finally {
            handshaking.unlock();
        }
    }

    enum BufType {
        PACKET,
        APPLICATION
    }

    static class Parameters extends HttpsParameters {
        private final InetSocketAddress address;
        private final HttpsConfigurator cfg;
        private SSLParameters params;

        Parameters(HttpsConfigurator cfg, InetSocketAddress address) {
            this.address = address;
            this.cfg = cfg;
        }

        public InetSocketAddress getClientAddress() {
            return address;
        }

        public HttpsConfigurator getHttpsConfigurator() {
            return cfg;
        }

        SSLParameters getSSLParameters() {
            return params;
        }

        public void setSSLParameters(SSLParameters p) {
            params = p;
        }
    }

    static class WrapperResult {
        SSLEngineResult result;

        /* if passed in buffer was not big enough then the reallocated buffer is returned here
         */
        ByteBuffer buf;
    }

    /**
     * This is a thin wrapper over SSLEngine and the SocketChannel,
     * which guarantees the ordering of wraps/unwraps with respect to the underlying
     * channel read/writes. It handles the UNDER/OVERFLOW status codes
     * It does not handle the handshaking status codes, or the CLOSED status code
     * though once the engine is closed, any attempt to read/write to it
     * will get an exception.  The overall result is returned.
     * It functions synchronously/blocking
     */
    class EngineWrapper {

        private final Object wrapLock;
        private final Object unwrapLock;
        private final SocketChannel chan;
        private final SSLEngine engine;
        boolean closed = false;
        int uRemaining; // the number of bytes left in unwrap_src after an unwrap()
        private ByteBuffer unwrapSrc;
        private ByteBuffer wrapDst;

        EngineWrapper(SocketChannel chan, SSLEngine engine) {
            this.chan = chan;
            this.engine = engine;
            wrapLock = new Object();
            unwrapLock = new Object();
            unwrapSrc = allocate(BufType.PACKET);
            wrapDst = allocate(BufType.PACKET);
        }

        void close() {
        }

        /* try to wrap and send the data in src. Handles OVERFLOW.
         * Might block if there is an outbound blockage or if another
         * thread is calling wrap(). Also, might not send any data
         * if an unwrap is needed.
         */
        WrapperResult wrapAndSend(ByteBuffer src) throws IOException {
            return wrapAndSendX(src, false);
        }

        WrapperResult wrapAndSendX(ByteBuffer src, boolean ignoreClose) throws IOException {
            if (closed && !ignoreClose) {
                throw new IOException("Engine is closed");
            }
            SSLEngineResult.Status status;
            WrapperResult r = new WrapperResult();
            synchronized (wrapLock) {
                wrapDst.clear();
                do {
                    r.result = engine.wrap(src, wrapDst);
                    status = r.result.getStatus();
                    if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        wrapDst = realloc(wrapDst, true, BufType.PACKET);
                    }
                } while (status == SSLEngineResult.Status.BUFFER_OVERFLOW);
                if (status == SSLEngineResult.Status.CLOSED && !ignoreClose) {
                    closed = true;
                    return r;
                }
                if (r.result.bytesProduced() > 0) {
                    wrapDst.flip();
                    int l = wrapDst.remaining();
                    while (l > 0) {
                        l -= chan.write(wrapDst);
                    }
                }
            }
            return r;
        }

        /* block until a complete message is available and return it
         * in dst, together with the Result. dst may have been re-allocated
         * so caller should check the returned value in Result
         * If handshaking is in progress then, possibly no data is returned
         */
        WrapperResult recvAndUnwrap(ByteBuffer dst) throws IOException {
            SSLEngineResult.Status status;
            WrapperResult r = new WrapperResult();
            r.buf = dst;
            if (closed) {
                throw new IOException("Engine is closed");
            }
            boolean needData;
            if (uRemaining > 0) {
                unwrapSrc.compact();
                unwrapSrc.flip();
                needData = false;
            } else {
                unwrapSrc.clear();
                needData = true;
            }
            synchronized (unwrapLock) {
                int x;
                do {
                    if (needData) {
                        do {
                            x = chan.read(unwrapSrc);
                        } while (x == 0);
                        if (x == -1) {
                            throw new IOException("connection closed for reading");
                        }
                        unwrapSrc.flip();
                    }
                    r.result = engine.unwrap(unwrapSrc, r.buf);
                    status = r.result.getStatus();
                    if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        if (unwrapSrc.limit() == unwrapSrc.capacity()) {
                            /* buffer not big enough */
                            unwrapSrc = realloc(
                                    unwrapSrc, false, BufType.PACKET
                            );
                        } else {
                            /* Buffer not full, just need to read more
                             * data off the channel. Reset pointers
                             * for reading off SocketChannel
                             */
                            unwrapSrc.position(unwrapSrc.limit());
                            unwrapSrc.limit(unwrapSrc.capacity());
                        }
                        needData = true;
                    } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        r.buf = realloc(r.buf, true, BufType.APPLICATION);
                        needData = false;
                    } else if (status == SSLEngineResult.Status.CLOSED) {
                        closed = true;
                        r.buf.flip();
                        return r;
                    }
                } while (status != SSLEngineResult.Status.OK);
            }
            uRemaining = unwrapSrc.remaining();
            return r;
        }
    }

    /**
     * represents an SSL input stream. Multiple https requests can
     * be sent over one stream. closing this stream causes an SSL close
     * input.
     */
    class InputStream extends java.io.InputStream {

        ByteBuffer byteBuffer;
        boolean closed = false;

        /* this stream eof */
        boolean eof = false;

        boolean needData = true;
        byte[] single = new byte[1];

        InputStream() {
            byteBuffer = allocate(BufType.APPLICATION);
        }

        public int read(byte[] buf, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("SSL stream is closed");
            }
            if (eof) {
                return -1;
            }
            int available = 0;
            if (!needData) {
                available = byteBuffer.remaining();
                needData = (available == 0);
            }
            if (needData) {
                byteBuffer.clear();
                WrapperResult r = recvData(byteBuffer);
                byteBuffer = r.buf == byteBuffer ? byteBuffer : r.buf;
                if ((available = byteBuffer.remaining()) == 0) {
                    eof = true;
                    return -1;
                } else {
                    needData = false;
                }
            }
            /* copy as much as possible from buf into users buf */
            if (len > available) {
                len = available;
            }
            byteBuffer.get(buf, off, len);
            return len;
        }

        public int available() {
            return byteBuffer.remaining();
        }

        public boolean markSupported() {
            return false; /* not possible with SSLEngine */
        }

        public void reset() throws IOException {
            throw new IOException("mark/reset not supported");
        }

        public long skip(long s) throws IOException {
            int n = (int) s;
            if (closed) {
                throw new IOException("SSL stream is closed");
            }
            if (eof) {
                return 0;
            }
            int ret = n;
            while (n > 0) {
                if (byteBuffer.remaining() >= n) {
                    byteBuffer.position(byteBuffer.position() + n);
                    return ret;
                } else {
                    n -= byteBuffer.remaining();
                    byteBuffer.clear();
                    WrapperResult r = recvData(byteBuffer);
                    byteBuffer = r.buf == byteBuffer ? byteBuffer : r.buf;
                }
            }
            return ret; /* not reached */
        }

        /**
         * close the SSL connection. All data must have been consumed
         * before this is called. Otherwise, an exception will be thrown.
         * [Note. May need to revisit this. not quite the normal close() semantics
         */
        public void close() throws IOException {
            eof = true;
            engine.closeInbound();
        }

        public int read(byte[] buf) throws IOException {
            return read(buf, 0, buf.length);
        }

        public int read() throws IOException {
            if (eof) {
                return -1;
            }
            int n = read(single, 0, 1);
            if (n <= 0) {
                return -1;
            } else {
                return single[0] & 0xFF;
            }
        }
    }

    /**
     * represents an SSL output stream. plain text data written to this stream
     * is encrypted by the stream. Multiple HTTPS responses can be sent on
     * one stream. closing this stream initiates an SSL closure
     */
    class OutputStream extends java.io.OutputStream {
        ByteBuffer buf;
        boolean closed = false;
        byte[] single = new byte[1];

        OutputStream() {
            buf = allocate(BufType.APPLICATION);
        }

        public void write(int b) throws IOException {
            single[0] = (byte) b;
            write(single, 0, 1);
        }

        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("output stream is closed");
            }
            while (len > 0) {
                int l = Math.min(len, buf.capacity());
                buf.clear();
                buf.put(b, off, l);
                len -= l;
                off += l;
                buf.flip();
                WrapperResult r = sendData(buf);
                if (r.result.getStatus() == SSLEngineResult.Status.CLOSED) {
                    closed = true;
                    if (len > 0) {
                        throw new IOException("output stream is closed");
                    }
                }
            }
        }

        public void flush() {
            /* no-op */
        }

        public void close() throws IOException {
            WrapperResult r;
            engine.closeOutbound();
            closed = true;
            SSLEngineResult.HandshakeStatus stat = SSLEngineResult.HandshakeStatus.NEED_WRAP;
            buf.clear();
            while (stat == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                r = wrapper.wrapAndSend(buf);
                stat = r.result.getHandshakeStatus();
            }
        }
    }
}
