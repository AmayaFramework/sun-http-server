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

package io.github.amayaframework.server.utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class HeaderMap implements Map<String, List<String>> {
    private final Map<String, List<String>> map;

    private HeaderMap(Map<String, List<String>> map) {
        Objects.requireNonNull(map);
        this.map = map;
    }

    public HeaderMap(int initialCapacity) {
        map = new ConcurrentHashMap<>(initialCapacity);
    }

    public HeaderMap() {
        map = new ConcurrentHashMap<>();
    }

    public static HeaderMap unmodifiableHeaderMap(HeaderMap headers) {
        Objects.requireNonNull(headers);
        Map<String, List<String>> retMap = new ConcurrentHashMap<>(headers.map.size());
        headers.map.forEach((key, value) -> retMap.put(key, Collections.unmodifiableList(value)));
        return new HeaderMap(Collections.unmodifiableMap(retMap));
    }

    private static void checkValue(String value) {
        int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c == '\r') {
                // is allowed if it is followed by \n and a whitespace char
                if (i >= len - 2) {
                    throw new IllegalArgumentException("Illegal CR found in header");
                }
                char c1 = value.charAt(i + 1);
                char c2 = value.charAt(i + 2);
                if (c1 != '\n') {
                    throw new IllegalArgumentException("Illegal char found after CR in header");
                }
                if (c2 != ' ' && c2 != '\t') {
                    throw new IllegalArgumentException("No whitespace found after CRLF in header");
                }
                i += 2;
            } else if (c == '\n') {
                throw new IllegalArgumentException("Illegal LF found in header");
            }
        }
    }

    /* Normalize the key by converting to following form.
     * First char upper case, rest lower case.
     * key is presumed to be ASCII
     */
    private String normalize(String key) {
        if (key == null) {
            return null;
        }
        int len = key.length();
        if (len == 0) {
            return key;
        }
        char[] b = key.toCharArray();
        if (b[0] >= 'a' && b[0] <= 'z') {
            b[0] = (char) (b[0] - ('a' - 'A'));
        } else if (b[0] == '\r' || b[0] == '\n')
            throw new IllegalArgumentException("illegal character in key");

        for (int i = 1; i < len; i++) {
            if (b[i] >= 'A' && b[i] <= 'Z') {
                b[i] = (char) (b[i] + ('a' - 'A'));
            } else if (b[i] == '\r' || b[i] == '\n')
                throw new IllegalArgumentException("illegal character in key");
        }
        return new String(b);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean containsKey(Object key) {
        if (key == null) {
            return false;
        }
        if (!(key instanceof String)) {
            return false;
        }
        return map.containsKey(normalize((String) key));
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    public List<String> get(Object key) {
        return map.get(normalize((String) key));
    }

    /**
     * returns the first value from the List of String values
     * for the given key (if at least one exists).
     *
     * @param key the key to search for
     * @return the first string value associated with the key
     */
    public String getFirst(String key) {
        List<String> l = get(key);
        if (l == null) {
            return null;
        }
        return l.get(0);
    }

    public List<String> put(String key, List<String> value) {
        for (String v : value)
            checkValue(v);
        return map.put(normalize(key), value);
    }

    /**
     * adds the given value to the list of headers
     * for the given key. If the mapping does not
     * already exist, then it is created
     *
     * @param key   the header name
     * @param value the header value to add to the header
     */
    public void add(String key, String value) {
        checkValue(value);
        String k = normalize(key);
        List<String> l = computeIfAbsent(k, k1 -> new LinkedList<>());
        l.add(value);
    }

    /**
     * sets the given value as the sole header value
     * for the given key. If the mapping does not
     * already exist, then it is created
     *
     * @param key   the header name
     * @param value the header value to set.
     */
    public void set(String key, String value) {
        LinkedList<String> l = new LinkedList<>();
        l.add(value);
        put(key, l);
    }

    public List<String> remove(Object key) {
        return map.remove(normalize((String) key));
    }

    @Override
    public void putAll(Map<? extends String, ? extends List<String>> m) {
        map.putAll(m);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<List<String>> values() {
        return map.values();
    }

    @Override
    public Set<Entry<String, List<String>>> entrySet() {
        return map.entrySet();
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
