/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.ErrorManager;

import javax.net.ssl.SSLContext;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.SocketHandler.Protocol;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SocketHandlerTests extends AbstractHandlerTest {

    private final InetAddress address;

    // https://bugs.openjdk.java.net/browse/JDK-8219991
    private final String JAVA_VERSION = System.getProperty("java.version");
    private final String JDK_8219991_ERROR_MESSAGE = "https://bugs.openjdk.java.net/browse/JDK-8219991";
    private final Boolean JDK_8219991 = JAVA_VERSION.startsWith("1.8")
            || (JAVA_VERSION.startsWith("11.0.8") && System.getProperty("java.vendor")
                    .contains("Oracle"));

    public SocketHandlerTests() throws UnknownHostException {
        address = InetAddress.getByName(System.getProperty("org.jboss.test.address", "127.0.0.1"));
    }

    @Test
    public void testTcpConnection() throws Exception {
        try (
                SimpleServer server = SimpleServer.createTcpServer();
                SocketHandler handler = createHandler(Protocol.TCP, server.getPort())) {
            final ExtLogRecord record = createLogRecord("Test TCP handler");
            handler.publish(record);
            final String msg = server.timeoutPoll();
            Assertions.assertNotNull(msg);
            Assertions.assertEquals("Test TCP handler", msg);
        }
    }

    @Test
    public void testTlsConnection() throws Exception {
        try (
                SimpleServer server = SimpleServer.createTlsServer();
                SocketHandler handler = createHandler(Protocol.SSL_TCP, server.getPort())) {
            final ExtLogRecord record = createLogRecord("Test TLS handler");
            handler.publish(record);
            final String msg = server.timeoutPoll();
            Assertions.assertNotNull(msg);
            Assertions.assertEquals("Test TLS handler", msg);
        }
    }

    @Test
    public void testUdpConnection() throws Exception {
        try (
                SimpleServer server = SimpleServer.createUdpServer();
                SocketHandler handler = createHandler(Protocol.UDP, server.getPort())) {
            final ExtLogRecord record = createLogRecord("Test UDP handler");
            handler.publish(record);
            final String msg = server.timeoutPoll();
            Assertions.assertNotNull(msg);
            Assertions.assertEquals("Test UDP handler", msg);
        }
    }

    @Test
    public void testTcpPortChange() throws Exception {
        try (
                SimpleServer server1 = SimpleServer.createTcpServer();
                SimpleServer server2 = SimpleServer.createTcpServer();
                SocketHandler handler = createHandler(Protocol.TCP, server1.getPort())) {
            final int port = server1.getPort();
            final int altPort = server2.getPort();
            ExtLogRecord record = createLogRecord("Test TCP handler " + port);
            handler.publish(record);
            String msg = server1.timeoutPoll();
            Assertions.assertNotNull(msg);
            Assertions.assertEquals("Test TCP handler " + port, msg);

            // Change the port on the handler which should close the first connection and open a new one
            handler.setPort(altPort);
            record = createLogRecord("Test TCP handler " + altPort);
            handler.publish(record);
            msg = server2.timeoutPoll();
            Assertions.assertNotNull(msg);
            Assertions.assertEquals("Test TCP handler " + altPort, msg);

            // There should be nothing on server1, we won't know if the real connection is closed but we shouldn't
            // have any data remaining on the first server
            Assertions.assertNull(server1.peek(), "Expected no data on server1");
        }
    }

    @Test
    public void testProtocolChange() throws Exception {
        Assumptions.assumeFalse(JDK_8219991, JDK_8219991_ERROR_MESSAGE);
        SocketHandler handler = null;
        try {
            try (SimpleServer server = SimpleServer.createTcpServer()) {
                handler = createHandler(Protocol.TCP, server.getPort());
                final ExtLogRecord record = createLogRecord("Test TCP handler");
                handler.publish(record);
                final String msg = server.timeoutPoll();
                Assertions.assertNotNull(msg);
                Assertions.assertEquals("Test TCP handler", msg);
            }
            // wait until the OS really release used port. https://issues.redhat.com/browse/LOGMGR-314
            Thread.sleep(50);

            // Change the protocol on the handler which should close the first connection and open a new one
            handler.setProtocol(Protocol.SSL_TCP);

            try (SimpleServer server = SimpleServer.createTlsServer(handler.getPort())) {
                final ExtLogRecord record = createLogRecord("Test TLS handler");
                handler.publish(record);
                final String msg = server.timeoutPoll();
                Assertions.assertNotNull(msg);
                Assertions.assertEquals("Test TLS handler", msg);
            }
        } finally {
            if (handler != null) {
                handler.close();
            }
        }
    }

    @Test
    public void testTcpReconnect() throws Exception {
        SocketHandler handler = null;
        try {

            // Publish a record to a running server
            try (
                    SimpleServer server = SimpleServer.createTcpServer()) {
                handler = createHandler(Protocol.TCP, server.getPort());
                handler.setErrorManager(AssertingErrorManager.of(ErrorManager.FLUSH_FAILURE));
                final ExtLogRecord record = createLogRecord("Test TCP handler");
                handler.publish(record);
                final String msg = server.timeoutPoll();
                Assertions.assertNotNull(msg);
                Assertions.assertEquals("Test TCP handler", msg);
            }
            // wait until the OS really release used port. https://issues.redhat.com/browse/LOGMGR-314
            Thread.sleep(50);

            // Publish a record to a down server, this likely won't put the handler in an error state yet. However once
            // we restart the server and loop the first socket should fail before a reconnect is attempted.
            final ExtLogRecord record = createLogRecord("Test TCP handler");
            handler.publish(record);
            try (
                    SimpleServer server = SimpleServer.createTcpServer(handler.getPort())) {
                final SocketHandler socketHandler = handler;
                // Keep writing a record until a successful record is published or a timeout occurs
                final String msg = timeout(() -> {
                    final ExtLogRecord r = createLogRecord("Test TCP handler");
                    socketHandler.publish(r);
                    try {
                        return server.poll();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }, 10);
                Assertions.assertNotNull(msg);
                Assertions.assertEquals("Test TCP handler", msg);
            }
        } finally {
            if (handler != null) {
                handler.close();
            }
        }
    }

    @Test
    public void testTlsConfig() throws Exception {
        Assumptions.assumeFalse(JDK_8219991, JDK_8219991_ERROR_MESSAGE);
        try (SimpleServer server = SimpleServer.createTlsServer()) {
            final int port = server.getPort();
            final LogContext logContext = LogContext.create();
            final PatternFormatter patternFormatter = new PatternFormatter("%s\n");
            final SocketHandler socketHandler = new SocketHandler();
            socketHandler.setSocketFactory(SSLContext.getDefault().getSocketFactory());
            socketHandler.setProtocol(Protocol.SSL_TCP);
            socketHandler.setAddress(address);
            socketHandler.setPort(port);
            socketHandler.setAutoFlush(true);
            socketHandler.setEncoding("utf-8");
            socketHandler.setFormatter(patternFormatter);
            socketHandler.setErrorManager(AssertingErrorManager.of());
            // Create the root logger
            final Logger rootLogger = logContext.getLogger("");
            rootLogger.addHandler(socketHandler);
            rootLogger.info("Test TCP handler " + port + " 1");
            String msg = server.timeoutPoll();
            Assertions.assertNotNull(msg);
            Assertions.assertEquals("Test TCP handler " + port + " 1", msg);
        }
    }

    private SocketHandler createHandler(final Protocol protocol, final int port) throws UnsupportedEncodingException {
        final SocketHandler handler = new SocketHandler(protocol, address, port);
        handler.setAutoFlush(true);
        handler.setEncoding("utf-8");
        handler.setFormatter(new PatternFormatter("%s\n"));
        handler.setErrorManager(AssertingErrorManager.of());

        return handler;
    }

    private static <R> R timeout(final Supplier<R> supplier, final int timeout) throws InterruptedException {
        R value = null;
        long t = timeout * 1000L;
        final long sleep = 100L;
        while (t > 0) {
            final long before = System.currentTimeMillis();
            value = supplier.get();
            if (value != null) {
                break;
            }
            t -= (System.currentTimeMillis() - before);
            TimeUnit.MILLISECONDS.sleep(sleep);
            t -= sleep;
        }
        Assertions.assertFalse((t <= 0), () -> String.format("Failed to get value in %d seconds.", timeout));
        return value;
    }
}
