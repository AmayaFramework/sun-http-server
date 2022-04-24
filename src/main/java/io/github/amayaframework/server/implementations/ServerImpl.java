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

import com.github.romanqed.util.http.HeaderMap;
import com.github.romanqed.util.http.HttpCode;
import io.github.amayaframework.server.events.Event;
import io.github.amayaframework.server.events.WriteFinishedEvent;
import io.github.amayaframework.server.interfaces.Filter;
import io.github.amayaframework.server.interfaces.HttpContext;
import io.github.amayaframework.server.interfaces.HttpExchange;
import io.github.amayaframework.server.interfaces.HttpHandler;
import io.github.amayaframework.server.streams.LeftOverInputStream;
import io.github.amayaframework.server.streams.ReadStream;
import io.github.amayaframework.server.streams.WriteStream;
import io.github.amayaframework.server.utils.ContextList;
import io.github.amayaframework.server.utils.Formats;
import io.github.amayaframework.server.utils.HttpsConfigurator;
import io.github.amayaframework.server.utils.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.channels.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.Executor;

public class ServerImpl {
    private final static int CLOCK_TICK = ServerConfig.getClockTick();
    private final static long IDLE_INTERVAL = ServerConfig.getIdleInterval();
    private final static int MAX_IDLE_CONNECTIONS = ServerConfig.getMaxIdleConnections();
    private final static long TIMER_MILLIS = ServerConfig.getTimerMillis();
    private final static long MAX_REQ_TIME = Formats.getTimeMillis(ServerConfig.getMaxReqTime());
    private final static long MAX_RSP_TIME = Formats.getTimeMillis(ServerConfig.getMaxRspTime());
    final static boolean timer1Enabled = MAX_REQ_TIME != -1 || MAX_RSP_TIME != -1;
    private final String protocol;
    private final boolean https;
    private final ContextList contexts;
    private final ServerSocketChannel socketChannel;
    private final Selector selector;
    private final SelectionKey listenerKey;
    private final Set<HttpConnection> idleConnections;
    private final Set<HttpConnection> allConnections;
    /* following two are used to keep track of the times
     * when a connection/request is first received
     * and when we start to send the response
     */
    private final Set<HttpConnection> requestConnections;
    private final Set<HttpConnection> responseConnections;
    private final Object lock = new Object();
    private final Timer timer;
    private final Logger logger = LoggerFactory.getLogger(ServerImpl.class);
    Dispatcher dispatcher;
    private Executor executor;
    private HttpsConfigurator httpsConfig;
    private SSLContext sslContext;
    private List<Event> events;
    private volatile boolean finished = false;
    private volatile boolean terminating = false;
    private boolean bound = false;
    private boolean started = false;
    private volatile long time;  /* current time */
    /* number of clock ticks since server started */
    private Timer timer1;
    private int exchangeCount = 0;

    protected ServerImpl(String protocol, InetSocketAddress address, int backlog) throws IOException {
        this.protocol = protocol;
        https = protocol.equalsIgnoreCase("https");
        contexts = new ContextList();
        socketChannel = ServerSocketChannel.open();
        if (address != null) {
            ServerSocket socket = socketChannel.socket();
            socket.bind(address, backlog);
            bound = true;
        }
        selector = Selector.open();
        socketChannel.configureBlocking(false);
        listenerKey = socketChannel.register(selector, SelectionKey.OP_ACCEPT);
        dispatcher = new Dispatcher();
        idleConnections = Collections.synchronizedSet(new HashSet<>());
        allConnections = Collections.synchronizedSet(new HashSet<>());
        requestConnections = Collections.synchronizedSet(new HashSet<>());
        responseConnections = Collections.synchronizedSet(new HashSet<>());
        time = System.currentTimeMillis();
        timer = new Timer("server-timer", true);
        timer.schedule(new ServerTimerTask(), CLOCK_TICK, CLOCK_TICK);
        if (timer1Enabled) {
            timer1 = new Timer("server-timer1", true);
            timer1.schedule(new ServerTimerTask1(), TIMER_MILLIS, TIMER_MILLIS);
            logger.info("HttpServer timer1 enabled period in ms:  " + TIMER_MILLIS);
            logger.info("MAX_REQ_TIME:  " + MAX_REQ_TIME);
            logger.info("MAX_RSP_TIME:  " + MAX_RSP_TIME);
        }
        events = new LinkedList<>();
        logger.info("HttpServer created " + protocol + " " + address);
    }

    public void bind(InetSocketAddress address, int backlog) throws IOException {
        if (bound) {
            throw new BindException("HttpServer already bound");
        }
        if (address == null) {
            throw new NullPointerException("null address");
        }
        ServerSocket socket = socketChannel.socket();
        socket.bind(address, backlog);
        bound = true;
    }

    public void start() {
        if (!bound || started || finished) {
            throw new IllegalStateException("server in wrong state");
        }
        if (executor == null) {
            executor = Runnable::run;
        }
        Thread t = new Thread(dispatcher);
        started = true;
        t.start();
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        if (started) {
            throw new IllegalStateException("server already started");
        }
        this.executor = executor;
    }

    public HttpsConfigurator getHttpsConfigurator() {
        return httpsConfig;
    }

    public void setHttpsConfigurator(HttpsConfigurator config) {
        if (config == null) {
            throw new NullPointerException("null HttpsConfigurator");
        }
        if (started) {
            throw new IllegalStateException("server already started");
        }
        this.httpsConfig = config;
        sslContext = config.getSSLContext();
    }

    private void delay() {
        Thread.yield();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop(int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("negative delay parameter");
        }
        terminating = true;
        try {
            socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        selector.wakeup();
        long latest = System.currentTimeMillis() + delay * 1000L;
        while (System.currentTimeMillis() < latest) {
            delay();
            if (finished) {
                break;
            }
        }
        finished = true;
        selector.wakeup();
        synchronized (allConnections) {
            for (HttpConnection c : allConnections) {
                c.close();
            }
        }
        allConnections.clear();
        idleConnections.clear();
        timer.cancel();
        if (timer1Enabled) {
            timer1.cancel();
        }
    }

    public synchronized HttpContextImpl createContext(String path, HttpHandler handler) {
        if (handler == null || path == null) {
            throw new NullPointerException("null handler, or path parameter");
        }
        HttpContextImpl context = new HttpContextImpl(protocol, path, handler);
        contexts.add(context);
        logger.info("context created: " + path);
        return context;
    }

    public synchronized HttpContextImpl createContext(String path) {
        if (path == null) {
            throw new NullPointerException("null path parameter");
        }
        HttpContextImpl context = new HttpContextImpl(protocol, path, null);
        contexts.add(context);
        logger.info("context created: " + path);
        return context;
    }

    /* main server listener task */

    public synchronized void removeContext(String path) throws IllegalArgumentException {
        if (path == null) {
            throw new NullPointerException("null path parameter");
        }
        contexts.remove(protocol, path);
        logger.info("context removed: " + path);
    }

    public synchronized void removeContext(HttpContext context) throws IllegalArgumentException {
        if (!(context instanceof HttpContextImpl)) {
            throw new IllegalArgumentException("wrong HttpContext type");
        }
        contexts.remove((HttpContextImpl) context);
        logger.info("context removed: " + context.getPath());
    }

    public InetSocketAddress getAddress() {
        return AccessController.doPrivileged(
                (PrivilegedAction<InetSocketAddress>) () -> (InetSocketAddress) socketChannel.socket()
                        .getLocalSocketAddress());
    }

    public synchronized void startExchange() {
        exchangeCount++;
    }

    public synchronized int endExchange() {
        exchangeCount--;
        return exchangeCount;
    }

    public void requestStarted(HttpConnection c) {
        c.setCreationTime(time);
        c.setState(HttpConnection.State.REQUEST);
        requestConnections.add(c);
    }

    public void requestCompleted(HttpConnection c) {
        requestConnections.remove(c);
        c.setResponseStartedTime(time);
        responseConnections.add(c);
        c.setState(HttpConnection.State.RESPONSE);
    }

    public void responseCompleted(HttpConnection c) {
        responseConnections.remove(c);
        c.setState(HttpConnection.State.IDLE);
    }

    public void addEvent(Event event) {
        synchronized (lock) {
            events.add(event);
            selector.wakeup();
        }
    }

    private void closeConnection(HttpConnection conn) {
        conn.close();
        allConnections.remove(conn);
        switch (conn.getState()) {
            case REQUEST:
                requestConnections.remove(conn);
                break;
            case RESPONSE:
                responseConnections.remove(conn);
                break;
            case IDLE:
                idleConnections.remove(conn);
                break;
        }
        requestConnections.remove(conn);
        responseConnections.remove(conn);
        idleConnections.remove(conn);
    }

    class ServerTimerTask extends TimerTask {
        public void run() {
            LinkedList<HttpConnection> toClose = new LinkedList<>();
            time = System.currentTimeMillis();
            synchronized (idleConnections) {
                for (HttpConnection c : idleConnections) {
                    if (c.getTime() <= time) {
                        toClose.add(c);
                    }
                }
                for (HttpConnection c : toClose) {
                    idleConnections.remove(c);
                    allConnections.remove(c);
                    c.close();
                }
            }
        }
    }

    class ServerTimerTask1 extends TimerTask {

        // runs every TIMER_MILLIS
        public void run() {
            LinkedList<HttpConnection> toClose = new LinkedList<>();
            time = System.currentTimeMillis();
            synchronized (requestConnections) {
                if (MAX_REQ_TIME != -1) {
                    for (HttpConnection c : requestConnections) {
                        if (c.getCreationTime() + TIMER_MILLIS + MAX_REQ_TIME <= time) {
                            toClose.add(c);
                        }
                    }
                    for (HttpConnection c : toClose) {
                        logger.info("closing: no request: " + c);
                        requestConnections.remove(c);
                        allConnections.remove(c);
                        c.close();
                    }
                }
            }
            toClose = new LinkedList<>();
            synchronized (responseConnections) {
                if (MAX_RSP_TIME != -1) {
                    for (HttpConnection c : responseConnections) {
                        if (c.getResponseStartedTime() + TIMER_MILLIS + MAX_RSP_TIME <= time) {
                            toClose.add(c);
                        }
                    }
                    for (HttpConnection c : toClose) {
                        logger.info("closing: no response: " + c);
                        responseConnections.remove(c);
                        allConnections.remove(c);
                        c.close();
                    }
                }
            }
        }
    }

    class Exchange implements Runnable {
        private final SocketChannel channel;
        private final HttpConnection connection;
        private final String protocol;
        HttpContext context;
        InputStream rawIn;
        OutputStream rawOut;
        ExchangeImpl exchange;
        HttpContextImpl ctx;
        boolean rejected = false;

        Exchange(SocketChannel channel, String protocol, HttpConnection conn) {
            this.channel = channel;
            this.connection = conn;
            this.protocol = protocol;
        }

        public void run() {
            /* context will be null for new connections */
            context = connection.getHttpContext();
            boolean newConnection;
            SSLEngine engine = null;
            String requestLine;
            SSLStreams sslStreams = null;
            try {
                if (context != null) {
                    this.rawIn = connection.getInputStream();
                    this.rawOut = connection.getRawOutputStream();
                    newConnection = false;
                } else {
                    /* figure out what kind of connection this is */
                    newConnection = true;
                    if (https) {
                        if (sslContext == null) {
                            logger.warn("SSL connection received. No https context created");
                            throw new HttpException("No SSL context established");
                        }
                        sslStreams = new SSLStreams(httpsConfig, sslContext, channel);
                        rawIn = sslStreams.getInputStream();
                        rawOut = sslStreams.getOutputStream();
                        engine = sslStreams.getSSLEngine();
                        connection.setSslStreams(sslStreams);
                    } else {
                        rawIn = new BufferedInputStream(new ReadStream(channel));
                        rawOut = new WriteStream(channel);
                    }
                    connection.setRawInputStream(rawIn);
                    connection.setRawOutputStream(rawOut);
                }
                Request req = new Request(rawIn, rawOut);
                requestLine = req.requestLine();
                if (requestLine == null) {
                    /* connection closed */
                    closeConnection(connection);
                    return;
                }
                int space = requestLine.indexOf(' ');
                if (space == -1) {
                    reject(HttpCode.BAD_REQUEST, "Bad request line");
                    return;
                }
                String method = requestLine.substring(0, space);
                int start = space + 1;
                space = requestLine.indexOf(' ', start);
                if (space == -1) {
                    reject(HttpCode.BAD_REQUEST, "Bad request line");
                    return;
                }
                String uriStr = requestLine.substring(start, space);
                URI uri = new URI(uriStr);
                start = space + 1;
                String version = requestLine.substring(start);
                HeaderMap headers = req.headers();
                /* check key for illegal characters */
                for (String k : headers.keySet()) {
                    if (!Formats.isValidHeaderKey(k)) {
                        reject(HttpCode.BAD_REQUEST, "Header key contains illegal characters");
                        return;
                    }
                }
                /* checks for unsupported combinations of lengths and encodings */
                if (headers.containsKey("Content-Length") &&
                        (headers.containsKey("Transfer-encoding") || headers.get("Content-Length").size() > 1)) {
                    reject(HttpCode.BAD_REQUEST, "Conflicting or malformed headers detected");
                    return;
                }
                long clen = 0L;
                String headerValue = null;
                List<String> teValueList = headers.get("Transfer-encoding");
                if (teValueList != null && !teValueList.isEmpty()) {
                    headerValue = teValueList.get(0);
                }
                if (headerValue != null) {
                    if (headerValue.equalsIgnoreCase("chunked") && teValueList.size() == 1) {
                        clen = -1L;
                    } else {
                        reject(HttpCode.NOT_IMPLEMENTED, "Unsupported Transfer-Encoding value");
                        return;
                    }
                } else {
                    headerValue = headers.getFirst("Content-Length");
                    if (headerValue != null) {
                        clen = Long.parseLong(headerValue);
                    }
                    if (clen == 0) {
                        requestCompleted(connection);
                    }
                }
                ctx = contexts.findContext(protocol, uri.getPath());
                if (ctx == null) {
                    reject(HttpCode.NOT_FOUND, "No context found for request");
                    return;
                }
                connection.setContext(ctx);
                if (ctx.getHandler() == null) {
                    reject(HttpCode.INTERNAL_SERVER_ERROR, "No handler for context");
                    return;
                }
                exchange = new ExchangeImpl(method, uri, req, clen, connection);
                String connectionHeader = headers.getFirst("Connection");
                HeaderMap rHeaders = exchange.getResponseHeaders();
                if (connectionHeader != null && connectionHeader.equalsIgnoreCase("close")) {
                    exchange.setClose(true);
                }
                if (version.equalsIgnoreCase("http/1.0")) {
                    exchange.setHttp10(true);
                    if (connectionHeader == null) {
                        exchange.setClose(true);
                        rHeaders.set("Connection", "close");
                    } else if (connectionHeader.equalsIgnoreCase("keep-alive")) {
                        rHeaders.set("Connection", "keep-alive");
                        int idle = (int) (ServerConfig.getIdleInterval() / 1000);
                        int max = ServerConfig.getMaxIdleConnections();
                        String val = "timeout=" + idle + ", max=" + max;
                        rHeaders.set("Keep-Alive", val);
                    }
                }

                if (newConnection) {
                    connection.setInputStream(rawIn);
                    connection.setRawOutputStream(rawOut);
                    connection.setChannel(channel);
                    connection.setEngine(engine);
                    connection.setSslStreams(sslStreams);
                    connection.setSslContext(sslContext);
                    connection.setProtocol(protocol);
                    connection.setContext(ctx);
                    connection.setRawInputStream(rawIn);
                }
                /* check if client sent an Expert 100 Continue.
                 * In that case, need to send an interim response.
                 * In future API may be modified to allow app to
                 * be involved in this process.
                 */
                String exp = headers.getFirst("Expect");
                if (exp != null && exp.equalsIgnoreCase("100-continue")) {
                    sendReply(HttpCode.CONTINUE, null);
                }
                /* uf is the list of filters seen/set by the user.
                 * sf is the list of filters established internally
                 * and which are not visible to the user. uc and sc
                 * are the corresponding Filter.Chains.
                 * They are linked together by a LinkHandler
                 * so that they can both be invoked in one call.
                 */
                List<Filter> sf = ctx.getSystemFilters();
                List<Filter> uf = ctx.getFilters();

                Filter.Chain sc = new Filter.Chain(sf, ctx.getHandler());
                Filter.Chain uc = new Filter.Chain(uf, new LinkHandler(sc));

                /* set up the two stream references */
                exchange.getRequestBody();
                exchange.getResponseBody();
                if (https) {
                    uc.doFilter(new HttpsExchangeImpl(exchange));
                } else {
                    uc.doFilter(new HttpExchangeImpl(exchange));
                }

            } catch (IOException e1) {
                logger.info("ServerImpl.Exchange (1)", e1);
                closeConnection(connection);
            } catch (NumberFormatException e3) {
                reject(HttpCode.BAD_REQUEST, "NumberFormatException thrown");
            } catch (URISyntaxException e) {
                reject(HttpCode.BAD_REQUEST, "URISyntaxException thrown");
            } catch (Exception e4) {
                logger.info("ServerImpl.Exchange (2)", e4);
                closeConnection(connection);
            }
        }

        /* used to link to 2 or more Filter.Chains together */

        void reject(HttpCode code, String message) {
            rejected = true;
            sendReply(code, "<h1>" + code.getCode() + " " + code.getMessage() + "</h1>" + message);
            closeConnection(connection);
        }

        void sendReply(HttpCode code, String text) {
            try {
                StringBuilder builder = new StringBuilder(512);
                builder.
                        append("HTTP/1.1 ").
                        append(code.getCode()).
                        append(' ').
                        append(code.getMessage()).
                        append("\r\n");
                if (text != null && text.length() != 0) {
                    builder.append("Content-Length: ")
                            .append(text.length()).append("\r\n")
                            .append("Content-Type: text/html\r\n");
                } else {
                    builder.append("Content-Length: 0\r\n");
                    text = "";
                }
                builder.append("\r\n").append(text);
                String s = builder.toString();
                byte[] b = s.getBytes("ISO8859_1");
                rawOut.write(b);
                rawOut.flush();
            } catch (IOException e) {
                e.printStackTrace();
                closeConnection(connection);
            }
        }

        class LinkHandler implements HttpHandler {
            private final Filter.Chain nextChain;

            LinkHandler(Filter.Chain nextChain) {
                this.nextChain = nextChain;
            }

            public void handle(HttpExchange exchange) throws IOException {
                nextChain.doFilter(exchange);
            }
        }
    }

    class Dispatcher implements Runnable {

        final LinkedList<HttpConnection> connectionsToRegister = new LinkedList<>();

        private void handleEvent(Event r) {
            ExchangeImpl t = r.getExchange();
            HttpConnection c = t.getConnection();
            try {
                if (r instanceof WriteFinishedEvent) {

                    int exchanges = endExchange();
                    if (terminating && exchanges == 0) {
                        finished = true;
                    }
                    responseCompleted(c);
                    LeftOverInputStream is = t.getOriginalInputStream();
                    if (!is.isEOF()) {
                        t.setClose(true);
                    }
                    if (t.isClose() || idleConnections.size() >= MAX_IDLE_CONNECTIONS) {
                        c.close();
                        allConnections.remove(c);
                    } else {
                        if (is.isDataBuffered()) {
                            /* don't re-enable the interest ops, just handle it */
                            requestStarted(c);
                            handle(c.getChannel(), c);
                        } else {
                            connectionsToRegister.add(c);
                        }
                    }
                }
            } catch (IOException e) {
                logger.info("Dispatcher (1)", e);
                c.close();
            }
        }

        void reRegister(HttpConnection c) {
            /* re-register with selector */
            try {
                SocketChannel chan = c.getChannel();
                chan.configureBlocking(false);
                SelectionKey key = chan.register(selector, SelectionKey.OP_READ);
                key.attach(c);
                c.setSelectionKey(key);
                c.setTime(time + IDLE_INTERVAL);
                idleConnections.add(c);
            } catch (IOException e) {
                logger.info("Dispatcher(8)", e);
                c.close();
            }
        }

        public void run() {
            while (!finished) {
                try {
                    List<Event> list = null;
                    synchronized (lock) {
                        if (events.size() > 0) {
                            list = events;
                            events = new LinkedList<>();
                        }
                    }

                    if (list != null) {
                        for (Event r : list) {
                            handleEvent(r);
                        }
                    }

                    for (HttpConnection c : connectionsToRegister) {
                        reRegister(c);
                    }
                    connectionsToRegister.clear();

                    selector.select(1000);

                    /* process the selected list now  */
                    Set<SelectionKey> selected = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selected.iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove();
                        if (key.equals(listenerKey)) {
                            if (terminating) {
                                continue;
                            }
                            SocketChannel chan = socketChannel.accept();

                            // Set TCP_NO_DELAY, if appropriate
                            if (ServerConfig.isNoDelay()) {
                                chan.socket().setTcpNoDelay(true);
                            }

                            if (chan == null) {
                                continue; /* cancel something ? */
                            }
                            chan.configureBlocking(false);
                            SelectionKey newKey = chan.register(selector, SelectionKey.OP_READ);
                            HttpConnection c = new HttpConnection(ServerImpl.this);
                            c.setSelectionKey(newKey);
                            c.setChannel(chan);
                            newKey.attach(c);
                            requestStarted(c);
                            allConnections.add(c);
                        } else {
                            try {
                                if (key.isReadable()) {
                                    SocketChannel chan = (SocketChannel) key.channel();
                                    HttpConnection conn = (HttpConnection) key.attachment();
                                    key.cancel();
                                    chan.configureBlocking(true);
                                    if (idleConnections.remove(conn)) {
                                        // was an idle connection so add it
                                        // to reqConnections set.
                                        requestStarted(conn);
                                    }
                                    handle(chan, conn);
                                }
                            } catch (CancelledKeyException e) {
                                handleException(key, null);
                            } catch (IOException e) {
                                handleException(key, e);
                            }
                        }
                    }
                    // call the selector just to process the cancelled keys
                    selector.selectNow();
                } catch (IOException e) {
                    logger.info("Dispatcher (4)", e);
                } catch (Exception e) {
                    logger.info("Dispatcher (7)", e);
                }
            }
            try {
                selector.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void handleException(SelectionKey key, Exception e) {
            HttpConnection conn = (HttpConnection) key.attachment();
            if (e != null) {
                logger.info("Dispatcher (2)", e);
            }
            closeConnection(conn);
        }

        public void handle(SocketChannel chan, HttpConnection conn) {
            try {
                Exchange t = new Exchange(chan, protocol, conn);
                executor.execute(t);
            } catch (HttpException e1) {
                logger.info("Dispatcher (4)", e1);
                closeConnection(conn);
            }
        }
    }
}
