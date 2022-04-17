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

import java.util.List;
import java.util.Map;

/**
 * HttpContext represents a mapping between the root URI path of an application
 * to a {@link HttpHandler} which is invoked to handle requests destined
 * for that path on the associated HttpServer or HttpsServer.
 * <p>
 * HttpContext instances are created by the create methods in HttpServer
 * and HttpsServer
 * <p>
 * A chain of {@link Filter} objects can be added to a HttpContext. All exchanges processed by the
 * context can be pre- and post-processed by each Filter in the chain.
 */
public interface HttpContext {

    /**
     * returns the handler for this context
     *
     * @return the HttpHandler for this context
     */
    HttpHandler getHandler();

    /**
     * Sets the handler for this context, if not already set.
     *
     * @param handler the handler to set for this context
     * @throws IllegalArgumentException if this context's handler is already set.
     * @throws NullPointerException     if handler is <code>null</code>
     */
    void setHandler(HttpHandler handler);

    /**
     * returns the path this context was created with
     *
     * @return this context's path
     */
    String getPath();

    /**
     * returns a mutable Map, which can be used to pass
     * configuration and other data to Filter modules
     * and to the context's exchange handler.
     * <p>
     * Every attribute stored in this Map will be visible to
     * every HttpExchange processed by this context
     *
     * @return attached attributes
     */
    Map<String, Object> getAttributes();

    /**
     * returns this context's list of Filters. This is the
     * actual list used by the server when dispatching requests
     * so modifications to this list immediately affect the handling of exchanges.
     *
     * @return list of {@link Filter}
     */
    List<Filter> getFilters();
}
