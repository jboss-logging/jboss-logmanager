package org.jboss.logmanager.handlers;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.SocketHandler.Protocol;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SocketHandlerTests extends AbstractHandlerTest {

    private final InetAddress address;
    private final int port;
    private final int altPort;

    public SocketHandlerTests() throws UnknownHostException {
        address = InetAddress.getByName(System.getProperty("org.jboss.test.address", "127.0.0.1"));
        port = Integer.parseInt(System.getProperty("org.jboss.test.port", Integer.toString(SocketHandler.DEFAULT_PORT)));
        altPort = Integer.parseInt(System.getProperty("org.jboss.test.alt.port", Integer.toString(SocketHandler.DEFAULT_PORT + 10000)));
    }

    @Test
    public void testTcpConnection() throws Exception {
        try (
                SimpleServer server = SimpleServer.createTcpServer(port);
                SocketHandler handler = createHandler(Protocol.TCP)
        ) {
            final ExtLogRecord record = createLogRecord("Test TCP handler");
            handler.doPublish(record);
            final String msg = server.poll();
            Assert.assertNotNull(msg);
            Assert.assertEquals("Test TCP handler", msg);
        }
    }

    @Test
    public void testTlsConnection() throws Exception {
        try (
                SimpleServer server = SimpleServer.createTlsServer(port);
                SocketHandler handler = createHandler(Protocol.SSL_TCP)
        ) {
            final ExtLogRecord record = createLogRecord("Test TLS handler");
            handler.doPublish(record);
            final String msg = server.poll();
            Assert.assertNotNull(msg);
            Assert.assertEquals("Test TLS handler", msg);
        }
    }

    @Test
    public void testUdpConnection() throws Exception {
        try (
                SimpleServer server = SimpleServer.createUdpServer(port);
                SocketHandler handler = createHandler(Protocol.UDP)
        ) {
            final ExtLogRecord record = createLogRecord("Test UDP handler");
            handler.doPublish(record);
            final String msg = server.poll();
            Assert.assertNotNull(msg);
            Assert.assertEquals("Test UDP handler", msg);
        }
    }

    @Test
    public void testTcpPortChange() throws Exception {
        try (
                SimpleServer server1 = SimpleServer.createTcpServer(port);
                SimpleServer server2 = SimpleServer.createTcpServer(altPort);
                SocketHandler handler = createHandler(Protocol.TCP)
        ) {
            ExtLogRecord record = createLogRecord("Test TCP handler "  + port);
            handler.doPublish(record);
            String msg = server1.poll();
            Assert.assertNotNull(msg);
            Assert.assertEquals("Test TCP handler " + port, msg);

            // Change the port on the handler which should close the first connection and open a new one
            handler.setPort(altPort);
            record = createLogRecord("Test TCP handler " + altPort);
            handler.doPublish(record);
            msg = server2.poll();
            Assert.assertNotNull(msg);
            Assert.assertEquals("Test TCP handler " + altPort, msg);

            // There should be nothing on server1, we won't know if the real connection is closed but we shouldn't
            // have any data remaining on the first server
            Assert.assertNull("Expected no data on server1", server1.peek());
        }
    }

    @Test
    public void testProtocolChange() throws Exception {
        try (SocketHandler handler = createHandler(Protocol.TCP)) {
            try (SimpleServer server = SimpleServer.createTcpServer(port)) {
                final ExtLogRecord record = createLogRecord("Test TCP handler");
                handler.doPublish(record);
                final String msg = server.poll();
                Assert.assertNotNull(msg);
                Assert.assertEquals("Test TCP handler", msg);
            }

            // Change the protocol on the handler which should close the first connection and open a new one
            handler.setProtocol(Protocol.SSL_TCP);

            try (SimpleServer server = SimpleServer.createTlsServer(port)) {
                final ExtLogRecord record = createLogRecord("Test TLS handler");
                handler.doPublish(record);
                final String msg = server.poll();
                Assert.assertNotNull(msg);
                Assert.assertEquals("Test TLS handler", msg);
            }
        }
    }

    private SocketHandler createHandler(final Protocol protocol) throws UnsupportedEncodingException {
        final SocketHandler handler = new SocketHandler(protocol, address, port);
        handler.setAutoFlush(true);
        handler.setEncoding("utf-8");
        handler.setFormatter(new PatternFormatter("%s\n"));

        return handler;
    }
}
