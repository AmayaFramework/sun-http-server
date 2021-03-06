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

import io.github.amayaframework.server.interfaces.Filter;
import io.github.amayaframework.server.interfaces.HttpContext;
import io.github.amayaframework.server.interfaces.HttpHandler;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class HttpContextImpl implements HttpContext {
    private final String path;
    private final String protocol;
    private final Map<String, Object> attributes = new HashMap<>();
    /* system filters, not visible to applications */
    private final LinkedList<Filter> systemFilters = new LinkedList<>();
    /* user filters, set by applications */
    private final LinkedList<Filter> userFilters = new LinkedList<>();
    private HttpHandler handler;


    public HttpContextImpl(String protocol, String path, HttpHandler httpHandler) {
        if (path == null || protocol == null || path.length() < 1 || path.charAt(0) != '/') {
            throw new IllegalArgumentException("Illegal value for path or protocol");
        }
        this.protocol = protocol.toLowerCase();
        this.path = path;
        if (!this.protocol.equals("http") && !this.protocol.equals("https")) {
            throw new IllegalArgumentException("Illegal value for protocol");
        }
        this.handler = httpHandler;
    }

    /**
     * returns the handler for this context
     *
     * @return the HttpHandler for this context
     */
    public HttpHandler getHandler() {
        return handler;
    }

    public void setHandler(HttpHandler handler) {
        if (handler == null) {
            throw new NullPointerException("Null handler parameter");
        }
        if (this.handler != null) {
            throw new IllegalArgumentException("handler already set");
        }
        this.handler = handler;
    }

    /**
     * returns the path this context was created with
     *
     * @return this context's path
     */
    public String getPath() {
        return path;
    }

    /**
     * returns the protocol this context was created with
     *
     * @return this context's path
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * returns a mutable Map, which can be used to pass
     * configuration and other data to Filter modules
     * and to the context's exchange handler.
     * <p>
     * Every attribute stored in this Map will be visible to
     * every HttpExchange processed by this context
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public List<Filter> getFilters() {
        return userFilters;
    }

    public List<Filter> getSystemFilters() {
        return systemFilters;
    }
}
