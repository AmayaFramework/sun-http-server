/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

package io.github.amayaframework.server.interfaces;

import io.github.amayaframework.server.implementations.HttpsServerImpl;
import io.github.amayaframework.server.utils.HttpsConfigurator;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * This class is an extension of {@link HttpServer} which provides
 * support for HTTPS. <p>
 * A HttpsServer must have an associated {@link HttpsConfigurator} object
 * which is used to establish the SSL configuration for the SSL connections.
 * <p>
 * All other configuration is the same as for HttpServer.
 */


public interface HttpsServer extends HttpServer {
    /**
     * Create a <code>HttpsServer</code> instance which will bind to the
     * specified {@link java.net.InetSocketAddress} (IP address and port number)
     * <p>
     * A maximum backlog can also be specified. This is the maximum number of
     * queued incoming connections to allow on the listening socket.
     * Queued TCP connections exceeding this limit may be rejected by the TCP implementation.
     * The server must have a HttpsConfigurator established with {@link #setHttpsConfigurator(HttpsConfigurator)}
     *
     * @param address the address to listen on, if <code>null</code> then bind() must be called
     *                to set the address
     * @param backlog the socket backlog. If this value is less than or equal to zero,
     *                then a system default value is used.
     */
    static HttpsServer create(InetSocketAddress address, int backlog) throws IOException {
        return new HttpsServerImpl(address, backlog);
    }

    /**
     * creates a HttpsServer instance which is initially not bound to any local address/port.
     * The server must be bound using {@link #bind(InetSocketAddress, int)} before it can be used.
     * The server must also have a HttpsConfigurator established with {@link #setHttpsConfigurator(HttpsConfigurator)}
     */
    static HttpsServer create() throws IOException {
        return create(null, 0);
    }

    /**
     * Gets this server's {@link HttpsConfigurator} object, if it has been set.
     *
     * @return the HttpsConfigurator for this server, or <code>null</code> if not set.
     */
    HttpsConfigurator getHttpsConfigurator();

    /**
     * Sets this server's {@link HttpsConfigurator} object.
     *
     * @param config the HttpsConfigurator to set
     * @throws NullPointerException if config is null.
     */
    void setHttpsConfigurator(HttpsConfigurator config);
}
