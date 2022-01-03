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

package io.github.amayaframework.server.streams;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ReadStream extends InputStream {
    private final static int BUFF_SIZE = 8 * 1024;
    private final SocketChannel channel;
    private final ByteBuffer channelBuf;
    private final byte[] one;
    private ByteBuffer markBuf; /* reads may be satisfied from this buffer */
    private boolean marked;
    private boolean reset;
    private boolean closed, eof = false;

    public ReadStream(SocketChannel chan) {
        this.channel = chan;
        channelBuf = ByteBuffer.allocate(BUFF_SIZE);
        channelBuf.clear();
        one = new byte[1];
        closed = marked = reset = false;
    }

    public synchronized int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public synchronized int read() throws IOException {
        int result = read(one, 0, 1);
        if (result == 1) {
            return one[0] & 0xFF;
        } else {
            return -1;
        }
    }

    public synchronized int read(byte[] b, int off, int sourceLength) throws IOException {

        int canReturn, willReturn;

        if (closed)
            throw new IOException("Stream closed");

        if (eof) {
            return -1;
        }

        if (off < 0 || sourceLength < 0 || sourceLength > (b.length - off)) {
            throw new IndexOutOfBoundsException();
        }

        if (reset) { /* satisfy from markBuf */
            canReturn = markBuf.remaining();
            willReturn = Math.min(canReturn, sourceLength);
            markBuf.get(b, off, willReturn);
            if (canReturn == willReturn) {
                reset = false;
            }
        } else { /* satisfy from channel */
            channelBuf.clear();
            if (sourceLength < BUFF_SIZE) {
                channelBuf.limit(sourceLength);
            }
            do {
                willReturn = channel.read(channelBuf);
            } while (willReturn == 0);
            if (willReturn == -1) {
                eof = true;
                return -1;
            }
            channelBuf.flip();
            channelBuf.get(b, off, willReturn);

            if (marked) { /* copy into markBuf */
                try {
                    markBuf.put(b, off, willReturn);
                } catch (BufferOverflowException e) {
                    marked = false;
                }
            }
        }
        return willReturn;
    }

    public boolean markSupported() {
        return true;
    }

    /* Does not query the OS socket */
    public synchronized int available() throws IOException {
        if (closed)
            throw new IOException("Stream is closed");

        if (eof)
            return -1;

        if (reset)
            return markBuf.remaining();

        return channelBuf.remaining();
    }

    public void close() throws IOException {
        if (closed) {
            return;
        }
        channel.close();
        closed = true;
    }

    public synchronized void mark(int readLimit) {
        if (closed)
            return;
        markBuf = ByteBuffer.allocate(readLimit);
        marked = true;
        reset = false;
    }

    public synchronized void reset() throws IOException {
        if (closed)
            return;
        if (!marked)
            throw new IOException("Stream not marked");
        marked = false;
        reset = true;
        markBuf.flip();
    }
}
