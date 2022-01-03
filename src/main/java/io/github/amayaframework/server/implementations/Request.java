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

import io.github.amayaframework.server.utils.HeaderMap;
import io.github.amayaframework.server.utils.ServerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Request {
    private final static int BUF_LEN = 2048;
    private final static byte CR = 13;
    private final static byte LF = 10;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final char[] buf = new char[BUF_LEN];
    int pos;
    StringBuffer lineBuf;
    HeaderMap headers = null;
    private String startLine;

    public Request(InputStream inputStream, OutputStream outputStream) throws IOException {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        do {
            startLine = readLine();
            if (startLine == null) {
                return;
            }
            /* skip blank lines */
        } while (startLine.equals(""));
    }

    public InputStream inputStream() {
        return inputStream;
    }

    public OutputStream outputStream() {
        return outputStream;
    }

    /**
     * read a line from the stream returning as a String.
     * Not used for reading headers.
     */

    public String readLine() throws IOException {
        boolean gotCR = false, gotLF = false;
        pos = 0;
        lineBuf = new StringBuffer();
        while (!gotLF) {
            int c = inputStream.read();
            if (c == -1) {
                return null;
            }
            if (gotCR) {
                if (c == LF) {
                    gotLF = true;
                } else {
                    gotCR = false;
                    consume(CR);
                    consume(c);
                }
            } else {
                if (c == CR) {
                    gotCR = true;
                } else {
                    consume(c);
                }
            }
        }
        lineBuf.append(buf, 0, pos);
        return new String(lineBuf);
    }

    private void consume(int c) {
        if (pos == BUF_LEN) {
            lineBuf.append(buf);
            pos = 0;
        }
        buf[pos++] = (char) c;
    }

    /**
     * returns the request line (first line of a request)
     */
    public String requestLine() {
        return startLine;
    }

    @SuppressWarnings("fallthrough")
    public HeaderMap headers() throws IOException {
        if (headers != null) {
            return headers;
        }
        headers = new HeaderMap();
        char[] s = new char[10];
        int len = 0;
        int firstChar = inputStream.read();
        // check for empty headers
        if (firstChar == CR || firstChar == LF) {
            int c = inputStream.read();
            if (c == CR || c == LF) {
                return headers;
            }
            s[0] = (char) firstChar;
            len = 1;
            firstChar = c;
        }
        while (firstChar != LF && firstChar != CR && firstChar >= 0) {
            int keyEnd = -1;
            int c;
            boolean inKey = firstChar > ' ';
            s[len++] = (char) firstChar;
            parseLoop:
            {
                while ((c = inputStream.read()) >= 0) {
                    switch (c) {
                        /*fallthrough*/
                        case ':':
                            if (inKey && len > 0)
                                keyEnd = len;
                            inKey = false;
                            break;
                        case '\t':
                            c = ' ';
                        case ' ':
                            inKey = false;
                            break;
                        case CR:
                        case LF:
                            firstChar = inputStream.read();
                            if (c == CR && firstChar == LF) {
                                firstChar = inputStream.read();
                                if (firstChar == CR)
                                    firstChar = inputStream.read();
                            }
                            if (firstChar == LF || firstChar == CR || firstChar > ' ')
                                break parseLoop;
                            /* continuation */
                            c = ' ';
                            break;
                    }
                    if (len >= s.length) {
                        char[] ns = new char[s.length * 2];
                        System.arraycopy(s, 0, ns, 0, len);
                        s = ns;
                    }
                    s[len++] = (char) c;
                }
                firstChar = -1;
            }
            while (len > 0 && s[len - 1] <= ' ')
                len--;
            String k;
            if (keyEnd <= 0) {
                k = null;
                keyEnd = 0;
            } else {
                k = String.copyValueOf(s, 0, keyEnd);
                if (keyEnd < len && s[keyEnd] == ':')
                    keyEnd++;
                while (keyEnd < len && s[keyEnd] <= ' ')
                    keyEnd++;
            }
            String v;
            if (keyEnd >= len)
                v = "";
            else
                v = String.copyValueOf(s, keyEnd, len - keyEnd);

            if (headers.size() >= ServerConfig.getMaxReqHeaders()) {
                throw new IOException(
                        "Maximum number of request headers exceeded, " + ServerConfig.getMaxReqHeaders() + "."
                );
            }

            headers.add(k, v);
            len = 0;
        }
        return headers;
    }
}
