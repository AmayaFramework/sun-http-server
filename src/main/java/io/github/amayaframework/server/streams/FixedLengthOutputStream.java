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

import java.io.IOException;
import java.io.OutputStream;

public class FixedLengthOutputStream extends AbstractLengthOutputStream {
    private long remaining;
    private boolean eof = false;

    public FixedLengthOutputStream(ExchangeImpl exchange, OutputStream src, long length) {
        super(exchange, src);
        this.remaining = length;
    }

    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("stream closed");
        }
        eof = (remaining == 0);
        if (eof) {
            throw new StreamClosedException();
        }
        out.write(b);
        remaining--;
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("stream closed");
        }
        eof = (remaining == 0);
        if (eof) {
            throw new StreamClosedException();
        }
        if (len > remaining) {
            // stream is still open, caller can retry
            throw new IOException("too many bytes to write to stream");
        }
        out.write(b, off, len);
        remaining -= len;
    }

    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (remaining > 0) {
            exchange.close();
            throw new IOException("insufficient bytes written to stream");
        }
        flush();
        eof = true;
        super.close();
    }
}
