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

import io.github.amayaframework.server.implementations.ExchangeImpl;
import io.github.amayaframework.server.utils.ServerConfig;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class LeftOverInputStream extends FilterInputStream {
    protected final ExchangeImpl exchange;
    private final byte[] one = new byte[1];
    protected boolean closed = false;
    protected boolean eof = false;

    public LeftOverInputStream(ExchangeImpl exchange, InputStream src) {
        super(src);
        this.exchange = exchange;
    }

    /**
     * if bytes are left over buffered on *the UNDERLYING* stream
     */
    public boolean isDataBuffered() throws IOException {
        return super.available() > 0;
    }

    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (!eof) {
            eof = drain(ServerConfig.getDrainAmount());
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isEOF() {
        return eof;
    }

    protected abstract int readImpl(byte[] b, int off, int len) throws IOException;

    public synchronized int read() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
        int c = readImpl(one, 0, 1);
        if (c == -1 || c == 0) {
            return c;
        } else {
            return one[0] & 0xFF;
        }
    }

    public synchronized int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
        return readImpl(b, off, len);
    }

    /**
     * read and discard up to l bytes or "eof" occurs,
     * (whichever is first). Then return true if the stream
     * is at eof (i.e. all bytes were read) or false if not
     * (still bytes to be read)
     */
    public boolean drain(long l) throws IOException {
        int bufSize = 2048;
        byte[] db = new byte[bufSize];
        while (l > 0) {
            long len = readImpl(db, 0, bufSize);
            if (len == -1) {
                eof = true;
                return true;
            } else {
                l = l - len;
            }
        }
        return false;
    }
}
