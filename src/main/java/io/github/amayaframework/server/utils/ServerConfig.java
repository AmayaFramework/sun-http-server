/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

public final class ServerConfig {

    private static final int DEFAULT_CLOCK_TICK = 10000; // 10 sec.
    /* These values must be a reasonable multiple of clockTick */
    private static final long DEFAULT_IDLE_INTERVAL = 30; // 5 min
    private static final int DEFAULT_MAX_IDLE_CONNECTIONS = 200;
    private static final long DEFAULT_MAX_REQ_TIME = -1; // default: forever
    private static final long DEFAULT_MAX_RSP_TIME = -1; // default: forever
    private static final long DEFAULT_TIMER_MILLIS = 1000;
    private static final int DEFAULT_MAX_REQ_HEADERS = 200;
    private static final long DEFAULT_DRAIN_AMOUNT = 64 * 1024;
    private static int clockTick = DEFAULT_CLOCK_TICK;
    private static long idleInterval = DEFAULT_IDLE_INTERVAL;
    // The maximum number of bytes to drain from an input stream
    private static long drainAmount = DEFAULT_DRAIN_AMOUNT;
    private static int maxIdleConnections = DEFAULT_MAX_IDLE_CONNECTIONS;
    // The maximum number of request headers allowable
    private static int maxReqHeaders = DEFAULT_MAX_REQ_HEADERS;
    // max time a request or response is allowed to take
    private static long maxReqTime = DEFAULT_MAX_REQ_TIME;
    private static long maxRspTime = DEFAULT_MAX_RSP_TIME;
    private static long timerMillis = DEFAULT_TIMER_MILLIS;
    private static boolean debug = false;
    // the value of the TCP_NO_DELAY socket-level option
    private static boolean noDelay = false;

    private ServerConfig() {
    }

    public static int getClockTick() {
        return clockTick;
    }

    public static void setClockTick(int clockTick) {
        ServerConfig.clockTick = clockTick;
    }

    public static long getIdleInterval() {
        return idleInterval;
    }

    public static void setIdleInterval(long idleInterval) {
        ServerConfig.idleInterval = idleInterval;
    }

    public static long getDrainAmount() {
        return drainAmount;
    }

    public static void setDrainAmount(long drainAmount) {
        ServerConfig.drainAmount = drainAmount;
    }

    public static int getMaxIdleConnections() {
        return maxIdleConnections;
    }

    public static void setMaxIdleConnections(int maxIdleConnections) {
        ServerConfig.maxIdleConnections = maxIdleConnections;
    }

    public static int getMaxReqHeaders() {
        return maxReqHeaders;
    }

    public static void setMaxReqHeaders(int maxReqHeaders) {
        ServerConfig.maxReqHeaders = maxReqHeaders;
    }

    public static long getMaxReqTime() {
        return maxReqTime;
    }

    public static void setMaxReqTime(long maxReqTime) {
        ServerConfig.maxReqTime = maxReqTime;
    }

    public static long getMaxRspTime() {
        return maxRspTime;
    }

    public static void setMaxRspTime(long maxRspTime) {
        ServerConfig.maxRspTime = maxRspTime;
    }

    public static long getTimerMillis() {
        return timerMillis;
    }

    public static void setTimerMillis(long timerMillis) {
        ServerConfig.timerMillis = timerMillis;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void setDebug(boolean debug) {
        ServerConfig.debug = debug;
    }

    public static boolean isNoDelay() {
        return noDelay;
    }

    public static void setNoDelay(boolean noDelay) {
        ServerConfig.noDelay = noDelay;
    }
}
