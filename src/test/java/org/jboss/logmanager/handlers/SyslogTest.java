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
