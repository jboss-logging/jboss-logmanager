/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager.handlers;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.AsyncHandler.OverflowAction;
import org.jboss.logmanager.handlers.SyslogHandler.Protocol;
import org.jboss.logmanager.handlers.SyslogHandler.SyslogType;
import org.junit.Before;
import org.junit.Test;

/**
 * These tests should not be run by default and should only be run individually. These are only available for
 * convenience.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SyslogTest {

    private String hostname;
    private int port;
    private boolean useOctetCounting;
    private String formatPattern;
    private int logCount;
    private String encoding;
    private String message;
    private String delimiter;

    @Before
    public void propertyInit() {
        hostname = System.getProperty("syslog.hostname", "localhost");
        port = Integer.parseInt(System.getProperty("syslog.port", "514"));
        useOctetCounting = Boolean.parseBoolean(System.getProperty("syslog.useOctectCounting", "false"));
        formatPattern = System.getProperty("syslog.formatPattern", "%s");
        logCount = Integer.parseInt(System.getProperty("syslog.logCount", "3"));
        encoding = System.getProperty("syslog.encoding");
        message = System.getProperty("syslog.message");
        delimiter = System.getProperty("syslog.delimiter", "\n");
    }

    @Test
    public void tcpLocal() throws Exception {
        port = 10514;
        // Setup the handler
        final SyslogHandler handler = createHandler();
        handler.setSyslogType(SyslogType.RFC5424);
        handler.setProtocol(Protocol.TCP);
        handler.setAutoFlush(true);

        // Run the tests
        tcpLocal(handler);
    }

    @Test
    public void tcpLocalAsync() throws Exception {
        port = 10514;
        // Setup the handler
        final SyslogHandler syslogHandler = createHandler();
        syslogHandler.setSyslogType(SyslogType.RFC5424);
        syslogHandler.setProtocol(Protocol.TCP);
        syslogHandler.setAutoFlush(true);

        final AsyncHandler asyncHandler = new AsyncHandler();
        asyncHandler.setAutoFlush(true);
        asyncHandler.addHandler(syslogHandler);
        asyncHandler.setOverflowAction(OverflowAction.BLOCK);
        // Block until connected
        syslogHandler.setBlockOnReconnect(true);

        // Run the tests
        tcpLocal(asyncHandler);
    }

    @Test
    public void tcpSyslog() throws Exception {
        // Setup the handler
        final SyslogHandler handler = createHandler();
        handler.setSyslogType(SyslogType.RFC5424);
        handler.setProtocol(Protocol.TCP);

        doLog(handler);
        handler.close();
    }

    @Test
    public void sslTcpSyslog() throws Exception {
        // Setup the handler
        final SyslogHandler handler = createHandler();
        handler.setSyslogType(SyslogType.RFC5424);
        handler.setProtocol(Protocol.SSL_TCP);

        doLog(handler);
        handler.close();
    }

    @Test
    public void udpSyslog() throws Exception {
        // Setup the handler
        final SyslogHandler handler = createHandler();
        handler.setSyslogType(SyslogType.RFC5424);
        handler.setProtocol(Protocol.UDP);

        doLog(handler);
        handler.close();
    }

    private void tcpLocal(final ExtHandler handler) throws Exception {
        SimpleLogServer server = SimpleLogServer.createTcp(System.out, InetAddress.getLocalHost(), port);
        try {

            doLog(handler);
            // Sleep just to make sure all is written before we close down the server
            TimeUnit.SECONDS.sleep(5L);

            // Close the server and spin up a new one
            server.close();
            // Allow a failure
            try {
                doLog(handler, 3);
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Spin up a new server
            server = SimpleLogServer.createTcp(System.out, InetAddress.getLocalHost(), port);
            // Sleep again for a moment just to give it chance to reconnect, 6 seconds should do as it should reconnect in 5
            TimeUnit.SECONDS.sleep(6L);

            doLog(handler, 6);
        } finally {
            handler.flush();
            // Async handlers might require a second to flush
            TimeUnit.SECONDS.sleep(1L);
            handler.close();
            // Allow everything to finish before we close dow the server
            TimeUnit.SECONDS.sleep(2L);
            server.close();
        }
    }

    private void doLog(final ExtHandler handler) throws Exception {
        doLog(handler, 0);
    }

    private void doLog(final ExtHandler handler, final int start) throws Exception {
        final int end = start + logCount;
        final Logger logger = Logger.getLogger(SyslogTest.class.getName());
        logger.addHandler(handler);
        for (int i = start; i < end; i++) {
            if (message == null) {
                logger.warning(String.format("This is a test syslog message. \n Iteration: %d", i));
            } else {
                logger.warning(message);
            }
        }
        logger.removeHandler(handler);
    }

    private SyslogHandler createHandler() throws IOException {
        // Setup the handler
        final SyslogHandler handler = new SyslogHandler(hostname, port);
        handler.setUseCountingFraming(useOctetCounting);
        handler.setEncoding(encoding);
        handler.setHostname("localhost");
        handler.setFormatter(new PatternFormatter(formatPattern));
        if (delimiter == null || delimiter.isEmpty()) {
            handler.setMessageDelimiter(null);
            handler.setUseMessageDelimiter(false);
        } else if ("%n".equals(delimiter)) {
            handler.setMessageDelimiter("\n");
            handler.setUseMessageDelimiter(true);
        } else if ("null".equalsIgnoreCase(delimiter)) {
            handler.setMessageDelimiter(null);
            handler.setUseMessageDelimiter(true);
        } else if ("%t".equalsIgnoreCase(delimiter)) {
            handler.setMessageDelimiter("\t");
            handler.setUseMessageDelimiter(true);
        } else {
            handler.setMessageDelimiter(delimiter);
            handler.setUseMessageDelimiter(true);
        }
        return handler;
    }
}
