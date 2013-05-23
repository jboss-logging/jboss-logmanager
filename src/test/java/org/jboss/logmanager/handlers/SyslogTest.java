package org.jboss.logmanager.handlers;

import java.io.IOException;

import org.jboss.logmanager.Logger;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.SyslogHandler.MessageTransfer;
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
    private String message;

    @Before
    public void propertyInit() {
        hostname = System.getProperty("syslog.hostname", "localhost");
        port = Integer.parseInt(System.getProperty("syslog.port", "514"));
        useOctetCounting = Boolean.parseBoolean(System.getProperty("syslog.useOctectCounting", "false"));
        formatPattern = System.getProperty("syslog.formatPattern", "%s%n");
        logCount = Integer.parseInt(System.getProperty("syslog.logCount", "3"));
        message = System.getProperty("syslog.message");
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

    private void doLog(final SyslogHandler handler) throws Exception {
        final Logger logger = Logger.getLogger(SyslogTest.class.getName());
        logger.addHandler(handler);
        for (int i = 0; i < logCount; i++) {
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
        if (useOctetCounting)
            handler.setMessageTransfer(MessageTransfer.OCTET_COUNTING);
        handler.setHostname("localhost");
        handler.setFormatter(new PatternFormatter(formatPattern));
        return handler;
    }
}
