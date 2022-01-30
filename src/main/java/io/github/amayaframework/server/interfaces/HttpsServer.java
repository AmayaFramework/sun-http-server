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

import io.github.amayaframework.server.utils.HttpsConfigurator;

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
