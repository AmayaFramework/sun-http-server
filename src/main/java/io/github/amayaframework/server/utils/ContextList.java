/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package io.github.amayaframework.server.utils;

import io.github.amayaframework.server.implementations.HttpContextImpl;

import java.util.LinkedList;

public class ContextList {
    private final LinkedList<HttpContextImpl> list = new LinkedList<>();

    public synchronized void add(HttpContextImpl ctx) {
        list.add(ctx);
    }

    public synchronized int size() {
        return list.size();
    }

    /* initially contexts are located only by protocol:path.
     * Context with the longest prefix matches (currently case-sensitive)
     */
    public synchronized HttpContextImpl findContext(String protocol, String path) {
        return findContext(protocol, path, false);
    }

    public synchronized HttpContextImpl findContext(String protocol, String path, boolean exact) {
        protocol = protocol.toLowerCase();
        String longest = "";
        HttpContextImpl lc = null;
        for (HttpContextImpl ctx : list) {
            if (!ctx.getProtocol().equals(protocol)) {
                continue;
            }
            String contentPath = ctx.getPath();
            if (exact && !contentPath.equals(path)) {
                continue;
            } else if (!exact && !path.startsWith(contentPath)) {
                continue;
            }
            if (contentPath.length() > longest.length()) {
                longest = contentPath;
                lc = ctx;
            }
        }
        return lc;
    }

    public synchronized void remove(String protocol, String path)
            throws IllegalArgumentException {
        HttpContextImpl ctx = findContext(protocol, path, true);
        if (ctx == null) {
            throw new IllegalArgumentException("cannot remove element from list");
        }
        list.remove(ctx);
    }

    public synchronized void remove(HttpContextImpl context)
            throws IllegalArgumentException {
        for (HttpContextImpl ctx : list) {
            if (ctx.equals(context)) {
                list.remove(ctx);
                return;
            }
        }
        throw new IllegalArgumentException("no such context in list");
    }
}
