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

import static java.security.AccessController.doPrivileged;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedAction;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.util.Collection;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;

/**
 * A syslog handler for logging to syslogd.
 * <p/>
 * This handler can write to syslog servers that accept the <a href="http://tools.ietf.org/html/rfc3164">RFC3164</a> (obsolete)
 * and <a href="http://tools.ietf.org/html/rfc5424">RFC5424</a> formats. Writes can be done via TCP, SSL over TCP or
 * UDP protocols. You can also override the {@link #setOutputStream(OutputStream) output stream} if a custom
 * protocol is needed.
 * <p/>
 *
 * <pre>
 * <table border="1">
 *  <thead>
 *      <tr>
 *          <td colspan="4">Configuration Properties:</td>
 *      </tr>
 *  </thead>
 *  <tbody>
 *      <tr>
 *          <th>Property</th>
 *          <th>Description</th>
 *          <th>Type</th>
 *          <th>Default</th>
 *      </tr>
 *      <tr>
 *          <td>serverHostname</td>
 *          <td>The address of the syslog server</td>
 *          <td>{@link String String}</td>
 *          <td>localhost</td>
 *      </tr>
 *      <tr>
 *          <td>port</td>
 *          <td>The port of the syslog server</td>
 *          <td>int</td>
 *          <td>514</td>
 *      </tr>
 *      <tr>
 *          <td>facility</td>
 *          <td>The facility used to calculate the priority of the log message</td>
 *          <td>{@link Facility Facility}</td>
 *          <td>{@link Facility#USER_LEVEL USER_LEVEL}</td>
 *      </tr>
 *      <tr>
 *          <td>appName</td>
 *          <td>The name of the application that is logging</td>
 *          <td>{@link String String}</td>
 *          <td>java</td>
 *      </tr>
 *      <tr>
 *          <td>hostname</td>
 *          <td>The name of the host the messages are being sent from. See {@link #setHostname(String)} for more
 * details</td>
 *          <td>{@link String String}</td>
 *          <td>{@code
 * null
 * }</td>
 *      </tr>
 *      <tr>
 *          <td>syslogType</td>
 *          <td>The type of the syslog used to format the message</td>
 *          <td>{@link SyslogType SyslogType}</td>
 *          <td>{@link SyslogType#RFC5424 RFC5424}</td>
 *      </tr>
 *      <tr>
 *          <td>protocol</td>
 *          <td>The protocol to send the message over</td>
 *          <td>{@link Protocol Protocol}</td>
 *          <td>{@link Protocol#UDP UDP}</td>
 *      </tr>
 *      <tr>
 *          <td>delimiter</td>
 *          <td>The delimiter to use at the end of the message if {@link #setUseMessageDelimiter(boolean) useDelimiter}
 * is set to {@code
 * true
 * }</td>
 *          <td>{@link String String}</td>
 *          <td>For {@link Protocol#UDP UDP} {@code
 * null
 * } - For {@link Protocol#TCP TCP} or {@link Protocol#SSL_TCP
 * SSL_TCP} {@code \n}</td>
 *      </tr>
 *      <tr>
 *          <td>useDelimiter</td>
 *          <td>Whether or not the message should be appended with a {@link #setMessageDelimiter(String)
 * delimiter}</td>
 *          <td>{@code boolean}</td>
 *          <td>For {@link Protocol#UDP UDP} {@code
 * false
 * } - For {@link Protocol#TCP TCP} or {@link Protocol#SSL_TCP
 * SSL_TCP} {@code
 * true
 * }</td>
 *      </tr>
 *      <tr>
 *          <td>useCountingFraming</td>
 *          <td>Prefixes the size of the message, mainly used for {@link Protocol#TCP TCP} or {@link Protocol#SSL_TCP
 * SSL_TCP}, connections to the message being sent to the syslog server. See <a href=
"http://tools.ietf.org/html/rfc6587#section-3.4.1">http://tools.ietf.org/html/rfc6587</a>
 * for more details on framing types.</td>
 *          <td>{@code boolean}</td>
 *          <td>{@code
 * false
 * }</td>
 *      </tr>
 *      <tr>
 *          <td>truncate</td>
 *          <td>Whether or not a message, including the header, should truncate the message if the length in bytes is
 * greater than the {@link #setMaxLength(int) maximum length}. If set to {@code
 * false
 * } messages will be split and sent
 * with the same header values.</td>
 *          <td>{@code boolean}</td>
 *          <td>{@code
 * true
 * }</td>
 *      </tr>
 *      <tr>
 *          <td>maxLength</td>
 *          <td>The maximum length a log message, including the header, is allowed to be.</td>
 *          <td>{@code int}</td>
 *          <td>For {@link SyslogType#RFC3164 RFC3164} 1024 (1k) - For {@link SyslogType#RFC5424 RFC5424} 2048
 * (2k)</td>
 *      </tr>
 *  </tbody>
 * </table>
 * </pre>
 * <p/>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({ "WeakerAccess", "unused" })
public class SyslogHandler extends ExtHandler {

    /**
     * The type of socket the syslog should write to
     */
    public static enum Protocol {
        /**
         * Transmission Control Protocol
         */
        TCP,
        /**
         * User Datagram Protocol
         */
        UDP,
        /**
         * Transport Layer Security over TCP
         */
        SSL_TCP,
    }

    /**
     * Severity as defined by RFC-5424 (<a href="http://tools.ietf.org/html/rfc5424">http://tools.ietf.org/html/rfc5424</a>)
     * and RFC-3164 (<a href="http://tools.ietf.org/html/rfc3164">http://tools.ietf.org/html/rfc3164</a>).
     */
    public static enum Severity {
        EMERGENCY(0, "Emergency: system is unusable"),
        ALERT(1, "Alert: action must be taken immediately"),
        CRITICAL(2, "Critical: critical conditions"),
        ERROR(3, "Error: error conditions"),
        WARNING(4, "Warning: warning conditions"),
        NOTICE(5, "Notice: normal but significant condition"),
        INFORMATIONAL(6, "Informational: informational messages"),
        DEBUG(7, "Debug: debug-level messages");

        final int code;
        final String desc;

        private Severity(final int code, final String desc) {
            this.code = code;
            this.desc = desc;
        }

        @Override
        public String toString() {
            return String.format("%s[%d,%s]", name(), code, desc);
        }

        /**
         * Maps a {@link Level level} to a {@link Severity severity}. By default returns {@link
         * Severity#INFORMATIONAL}.
         *
         * @param level the level to map
         *
         * @return the severity
         */
        // TODO (jrp) allow for a custom mapping
        public static Severity fromLevel(final Level level) {
            if (level == null) {
                throw new IllegalArgumentException("Level cannot be null");
            }
            final int levelValue = level.intValue();
            if (levelValue >= org.jboss.logmanager.Level.FATAL.intValue()) {
                return Severity.EMERGENCY;
            } else if (levelValue >= org.jboss.logmanager.Level.SEVERE.intValue()
                    || levelValue >= org.jboss.logmanager.Level.ERROR.intValue()) {
                return Severity.ERROR;
            } else if (levelValue >= org.jboss.logmanager.Level.WARN.intValue() || levelValue >= Level.WARNING.intValue()) {
                return Severity.WARNING;
            } else if (levelValue >= org.jboss.logmanager.Level.INFO.intValue()) {
                return Severity.INFORMATIONAL;
            } else if (levelValue >= org.jboss.logmanager.Level.TRACE.intValue() || levelValue >= Level.FINEST.intValue()) {
                // DEBUG for all TRACE, DEBUG, FINE, FINER, FINEST
                return Severity.DEBUG;
            }
            return Severity.INFORMATIONAL;
        }
    }

    /**
     * Facility as defined by RFC-5424 (<a href="http://tools.ietf.org/html/rfc5424">http://tools.ietf.org/html/rfc5424</a>)
     * and RFC-3164 (<a href="http://tools.ietf.org/html/rfc3164">http://tools.ietf.org/html/rfc3164</a>).
     */
    public static enum Facility {
        KERNEL(0, "kernel messages"),
        USER_LEVEL(1, "user-level messages"),
        MAIL_SYSTEM(2, "mail system"),
        SYSTEM_DAEMONS(3, "system daemons"),
        SECURITY(4, "security/authorization messages"),
        SYSLOGD(5, "messages generated internally by syslogd"),
        LINE_PRINTER(6, "line printer subsystem"),
        NETWORK_NEWS(7, "network news subsystem"),
        UUCP(8, "UUCP subsystem"),
        CLOCK_DAEMON(9, "clock daemon"),
        SECURITY2(10, "security/authorization messages"),
        FTP_DAEMON(11, "FTP daemon"),
        NTP(12, "NTP subsystem"),
        LOG_AUDIT(13, "log audit"),
        LOG_ALERT(14, "log alert"),
        CLOCK_DAEMON2(15, "clock daemon (note 2)"),
        LOCAL_USE_0(16, "local use 0  (local0)"),
        LOCAL_USE_1(17, "local use 1  (local1)"),
        LOCAL_USE_2(18, "local use 2  (local2)"),
        LOCAL_USE_3(19, "local use 3  (local3)"),
        LOCAL_USE_4(20, "local use 4  (local4)"),
        LOCAL_USE_5(21, "local use 5  (local5)"),
        LOCAL_USE_6(22, "local use 6  (local6)"),
        LOCAL_USE_7(23, "local use 7  (local7)");

        final int code;
        final String desc;
        final int octal;

        private Facility(final int code, final String desc) {
            this.code = code;
            this.desc = desc;
            octal = code * 8;
        }

        @Override
        public String toString() {
            return String.format("%s[%d,%s]", name(), code, desc);
        }
    }

    /**
     * The syslog type used for formatting the message.
     */
    public static enum SyslogType {
        /**
         * Formats the message according the the RFC-5424 specification
         * (<a href="http://tools.ietf.org/html/rfc5424#section-6">http://tools.ietf.org/html/rfc5424#section-6</a>
         */
        RFC5424,

        /**
         * Formats the message according the the RFC-3164 specification
         * (<a href="http://tools.ietf.org/html/rfc3164#section-4.1">http://tools.ietf.org/html/rfc3164#section-4.1</a>
         * <p>
         * Obsoleted by the {@link SyslogType#RFC5424}
         */
        @Deprecated
        RFC3164,
    }

    public static final InetAddress DEFAULT_ADDRESS;
    public static final int DEFAULT_PORT = 514;
    public static final int DEFAULT_SECURE_PORT = 6514;
    public static final String DEFAULT_ENCODING = "UTF-8";
    public static final Facility DEFAULT_FACILITY = Facility.USER_LEVEL;
    public static final String NILVALUE_SP = "- ";
    private static final Pattern PRINTABLE_ASCII_PATTERN = Pattern.compile("[\\P{Print} ]");

    static {
        try {
            DEFAULT_ADDRESS = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Could not create address to localhost");
        }
    }

    private InetAddress serverAddress;
    private int port;
    private String appName;
    private String hostname;
    private Facility facility;
    private SyslogType syslogType;
    private OutputStream out;
    private Protocol protocol;
    private boolean useCountingFraming;
    private boolean initializeConnection;
    private boolean outputStreamSet;
    private String delimiter;
    private boolean useDelimiter;
    private boolean truncate;
    private int maxLen;
    private boolean blockOnReconnect;
    private ClientSocketFactory clientSocketFactory;

    /**
     * The default class constructor.
     *
     * @throws IOException if an error occurs creating the UDP socket
     */
    public SyslogHandler() throws IOException {
        this(DEFAULT_ADDRESS, DEFAULT_PORT);
    }

    /**
     * Creates a new syslog handler that sends the messages to the server represented by the {@code serverHostname}
     * parameter on the port represented by the {@code port} parameter.
     *
     * @param serverHostname the server to send the messages to
     * @param port           the port the syslogd is listening on
     *
     * @throws IOException if an error occurs creating the UDP socket
     */
    public SyslogHandler(final String serverHostname, final int port) throws IOException {
        this(serverHostname, port, DEFAULT_FACILITY, null);
    }

    /**
     * Creates a new syslog handler that sends the messages to the server represented by the {@code serverAddress}
     * parameter on the port represented by the {@code port} parameter.
     *
     * @param serverAddress the server to send the messages to
     * @param port          the port the syslogd is listening on
     *
     * @throws IOException if an error occurs creating the UDP socket
     */
    public SyslogHandler(final InetAddress serverAddress, final int port) throws IOException {
        this(serverAddress, port, DEFAULT_FACILITY, null);
    }

    /**
     * Creates a new syslog handler that sends the messages to the server represented by the {@code serverAddress}
     * parameter on the port represented by the {@code port} parameter.
     *
     * @param serverHostname the server to send the messages to
     * @param port           the port the syslogd is listening on
     * @param facility       the facility to use when calculating priority
     * @param hostname       the name of the host the messages are being sent from see {@link #setHostname(String)} for
     *                       details on the hostname
     *
     * @throws IOException if an error occurs creating the UDP socket
     */
    public SyslogHandler(final String serverHostname, final int port, final Facility facility, final String hostname)
            throws IOException {
        this(serverHostname, port, facility, null, hostname);
    }

    /**
     * Creates a new syslog handler that sends the messages to the server represented by the {@code serverAddress}
     * parameter on the port represented by the {@code port} parameter.
     *
     * @param serverAddress the server to send the messages to
     * @param port          the port the syslogd is listening on
     * @param facility      the facility to use when calculating priority
     * @param hostname      the name of the host the messages are being sent from see {@link #setHostname(String)} for
     *                      details on the hostname
     *
     * @throws IOException if an error occurs creating the UDP socket
     */
    public SyslogHandler(final InetAddress serverAddress, final int port, final Facility facility, final String hostname)
            throws IOException {
        this(serverAddress, port, facility, null, hostname);
    }

    /**
     * Creates a new syslog handler that sends the messages to the server represented by the {@code serverAddress}
     * parameter on the port represented by the {@code port} parameter.
     *
     * @param serverHostname the server to send the messages to
     * @param port           the port the syslogd is listening on
     * @param facility       the facility to use when calculating priority
     * @param syslogType     the type of the syslog used to format the message
     * @param hostname       the name of the host the messages are being sent from see {@link #setHostname(String)} for
     *                       details on the hostname
     *
     * @throws IOException if an error occurs creating the UDP socket
     */
    public SyslogHandler(final String serverHostname, final int port, final Facility facility, final SyslogType syslogType,
            final String hostname) throws IOException {
        this(InetAddress.getByName(serverHostname), port, facility, syslogType, hostname);
    }

    /**
     * Creates a new syslog handler that sends the messages to the server represented by the {@code serverAddress}
     * parameter on the port represented by the {@code port} parameter.
     *
     * @param serverAddress the server to send the messages to
     * @param port          the port the syslogd is listening on
     * @param facility      the facility to use when calculating priority
     * @param syslogType    the type of the syslog used to format the message
     * @param hostname      the name of the host the messages are being sent from see {@link #setHostname(String)} for
     *                      details on the hostname
     *
     * @throws IOException if an error occurs creating the UDP socket
     */
    public SyslogHandler(final InetAddress serverAddress, final int port, final Facility facility, final SyslogType syslogType,
            final String hostname) throws IOException {
        this(serverAddress, port, facility, syslogType, null, hostname);
    }

    /**
     * Creates a new syslog handler that sends the messages to the server represented by the {@code serverAddress}
     * parameter on the port represented by the {@code port} parameter.
     *
     * @param serverHostname the server to send the messages to
     * @param port           the port the syslogd is listening on
     * @param facility       the facility to use when calculating priority
     * @param syslogType     the type of the syslog used to format the message
     * @param protocol       the socket type used to the connect to the syslog server
     * @param hostname       the name of the host the messages are being sent from see {@link #setHostname(String)} for
     *                       details on the hostname
     *
     * @throws IOException if an error occurs creating the UDP socket
     */
    public SyslogHandler(final String serverHostname, final int port, final Facility facility, final SyslogType syslogType,
            final Protocol protocol, final String hostname) throws IOException {
        this(InetAddress.getByName(serverHostname), port, facility, syslogType, protocol, hostname);
    }

    /**
     * Creates a new syslog handler that sends the messages to the server represented by the {@code serverAddress}
     * parameter on the port represented by the {@code port} parameter.
     *
     * @param serverAddress the server to send the messages to
     * @param port          the port the syslogd is listening on
     * @param facility      the facility to use when calculating priority
     * @param syslogType    the type of the syslog used to format the message
     * @param protocol      the socket type used to the connect to the syslog server
     * @param hostname      the name of the host the messages are being sent from see {@link #setHostname(String)} for
     *                      details on the hostname
     *
     * @throws IOException if an error occurs creating the UDP socket
     */
    public SyslogHandler(final InetAddress serverAddress, final int port, final Facility facility, final SyslogType syslogType,
            final Protocol protocol, final String hostname) throws IOException {
        setCharsetPrivate(StandardCharsets.UTF_8);
        this.serverAddress = serverAddress;
        this.port = port;
        this.facility = facility;
        this.appName = "java";
        this.hostname = checkPrintableAscii("host name", hostname);
        this.syslogType = (syslogType == null ? SyslogType.RFC5424 : syslogType);
        if (protocol == null) {
            this.protocol = Protocol.UDP;
            delimiter = null;
            useDelimiter = false;
        } else {
            this.protocol = protocol;
            if (protocol == Protocol.UDP) {
                delimiter = null;
                useDelimiter = false;
            } else if (protocol == Protocol.TCP || protocol == Protocol.SSL_TCP) {
                delimiter = "\n";
                useDelimiter = true;
            }
        }
        useCountingFraming = false;
        initializeConnection = true;
        outputStreamSet = false;
        truncate = true;
        if (this.syslogType == SyslogType.RFC3164) {
            maxLen = 1024;
        } else if (this.syslogType == SyslogType.RFC5424) {
            maxLen = 2048;
        }
        blockOnReconnect = false;
    }

    @Override
    public final void doPublish(final ExtLogRecord record) {
        // avoid reentrancy, which will generally cause a stack overflow
        if (lock.isHeldByCurrentThread()) {
            return;
        }
        lock.lock();
        try {
            init();
            if (out == null) {
                throw new IllegalStateException("The syslog handler has been closed.");
            }
            try {
                // Create the header
                final byte[] header;
                if (syslogType == SyslogType.RFC3164) {
                    header = createRFC3164Header(record);
                } else if (syslogType == SyslogType.RFC5424) {
                    header = createRFC5424Header(record);
                } else {
                    throw new IllegalStateException("The syslog type of '" + syslogType + "' is invalid.");
                }

                // Trailer in bytes
                final byte[] trailer = delimiter == null ? new byte[] { 0x00 } : delimiter.getBytes(StandardCharsets.UTF_8);

                // Buffer currently only has the header
                final int maxMsgLen = maxLen - (header.length + (useDelimiter ? trailer.length : 0));
                // Can't write the message if the header and trailer are bigger than the allowed length
                if (maxMsgLen < 1) {
                    throw new IOException(String.format(
                            "The header and delimiter length, %d, is greater than the message length, %d, allows.",
                            (header.length + (useDelimiter ? trailer.length : 0)), maxLen));
                }

                // Get the message
                final Formatter formatter = getFormatter();
                String logMsg;
                if (formatter != null) {
                    logMsg = formatter.format(record);
                } else {
                    logMsg = record.getFormattedMessage();
                }
                if (!Normalizer.isNormalized(logMsg, Form.NFKC)) {
                    logMsg = Normalizer.normalize(logMsg, Form.NFKC);
                }
                // Create a message buffer
                ByteStringBuilder message = new ByteStringBuilder(maxMsgLen);
                // Write the message to the buffer, the len is the number of characters written
                int len = message.write(logMsg, maxMsgLen);
                sendMessage(header, message, trailer);
                // If not truncating, chunk the message and send separately
                if (!truncate && len < logMsg.length()) {
                    while (len > 0) {
                        // Get the next part of the message to write
                        logMsg = logMsg.substring(len + 1);
                        if (logMsg.isEmpty()) {
                            break;
                        }
                        message = new ByteStringBuilder(maxMsgLen);
                        // Write the message to the buffer, the len is the number of characters written
                        len = message.write(logMsg, maxMsgLen);
                        sendMessage(header, message, trailer);
                    }
                }
            } catch (IOException e) {
                reportError("Could not write to syslog", e, ErrorManager.WRITE_FAILURE);
            }
        } finally {
            lock.unlock();
        }
        super.doPublish(record);
    }

    /**
     * Writes the message to the output stream. The message buffer is cleared after it's written.
     *
     * @param message the message to write
     *
     * @throws IOException if there is an error writing the message
     */
    private void sendMessage(final byte[] header, final ByteStringBuilder message, final byte[] trailer) throws IOException {
        final ByteStringBuilder payload = new ByteStringBuilder(header.length + message.length());
        // Prefix the size of the message if counting framing is being used
        if (useCountingFraming) {
            int len = header.length + message.length() + (useDelimiter ? trailer.length : 0);
            payload.append(len).append(' ');
        }
        payload.append(header);
        payload.append(message);
        if (useDelimiter)
            payload.append(trailer);
        out.write(payload.toArray());
        // If this is a TcpOutputStream print any errors that may have occurred
        if (out instanceof TcpOutputStream) {
            final Collection<Exception> errors = ((TcpOutputStream) out).getErrors();
            for (Exception error : errors) {
                reportError("Error writing to TCP stream", error, ErrorManager.WRITE_FAILURE);
            }
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            safeClose(out);
            out = null;
        } finally {
            lock.unlock();
        }
        super.close();
    }

    @Override
    public void flush() {
        lock.lock();
        try {
            safeFlush(out);
        } finally {
            lock.unlock();
        }
        super.flush();
    }

    /**
     * Gets app name used when formatting the message in RFC5424 format. By default the app name is &quot;java&quot;.
     *
     * @return the app name being used
     */
    public String getAppName() {
        lock.lock();
        try {
            return appName;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets app name used when formatting the message in RFC5424 format. By default the app name is &quot;java&quot;. If
     * set to {@code null} the {@link ExtLogRecord#getProcessName()} will be used.
     *
     * @param appName the app name to use
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public void setAppName(final String appName) {
        checkAccess();
        lock.lock();
        try {
            this.appName = checkPrintableAscii("app name", appName);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Indicates whether or not a {@link Protocol#TCP TCP} or {@link
     * Protocol#SSL_TCP SSL TCP} connection should block when attempting to
     * reconnect.
     *
     * @return {@code true} if blocking is enabled, otherwise {@code false}
     */
    public boolean isBlockOnReconnect() {
        lock.lock();
        try {
            return blockOnReconnect;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Enables or disables blocking when attempting to reconnect a {@link Protocol#TCP
     * TCP} or {@link Protocol#SSL_TCP SSL TCP} protocol.
     * <p/>
     * If set to {@code true} the {@code publish} methods will block when attempting to reconnect. This is only
     * advisable to be set to {@code true} if using an asynchronous handler.
     *
     * @param blockOnReconnect {@code true} to block when reconnecting or {@code false} to reconnect asynchronously
     *                         discarding any new messages coming in
     */
    public void setBlockOnReconnect(final boolean blockOnReconnect) {
        checkAccess();
        lock.lock();
        try {
            this.blockOnReconnect = blockOnReconnect;
            if (out instanceof TcpOutputStream) {
                ((TcpOutputStream) out).setBlockOnReconnect(blockOnReconnect);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the client socket factory used to create sockets.
     *
     * @param clientSocketFactory the client socket factory to use
     */
    public void setClientSocketFactory(final ClientSocketFactory clientSocketFactory) {
        checkAccess();
        lock.lock();
        try {
            this.clientSocketFactory = clientSocketFactory;
            initializeConnection = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks whether or not characters below decimal 32, traditional US-ASCII control values expect {@code DEL}, are
     * being escaped or not.
     *
     * @return {@code false}
     *
     * @deprecated escaping message values is not required per
     *             <a href="http://tools.ietf.org/html/rfc5424#section-6.2">RFC5424</a>
     *             and is no longer supported in this handler
     */
    @Deprecated
    public boolean isEscapeEnabled() {
        return false;
    }

    /**
     * <b>Note:</b> This method no longer does anything.
     * <p/>
     * Set to {@code true} to escape characters within the message string that are below decimal 32. These values are
     * tradition US-ASCII control values. The values will be replaced in a {@code #xxx} format where {@code xxx} is the
     * octal value of the character being replaced.
     *
     * @param escapeEnabled {@code true} to escape characters, {@code false} to not escape characters
     *
     * @deprecated escaping message values is not required per
     *             <a href="http://tools.ietf.org/html/rfc5424#section-6.2">RFC5424</a>
     *             and is no longer supported in this handler
     */
    @Deprecated
    public void setEscapeEnabled(final boolean escapeEnabled) {
        checkAccess();
    }

    /**
     * Returns the pid being used as the PROCID for RFC5424 messages.
     *
     * @return the pid or {@code null} if the pid could not be determined
     * @deprecated Always returns the current process ID, as a string.
     */
    @Deprecated(forRemoval = true, since = "3.1")
    public String getPid() {
        return String.valueOf(doPrivileged((PrivilegedAction<ProcessHandle>) ProcessHandle::current).pid());
    }

    /**
     * Returns the port the syslogd is listening on.
     *
     * @return the port
     */
    public int getPort() {
        lock.lock();
        try {
            return port;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the port the syslogd server is listening on.
     *
     * @param port the port
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public void setPort(final int port) {
        checkAccess();
        lock.lock();
        try {
            this.port = port;
            initializeConnection = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the facility used for calculating the priority of the message.
     *
     * @return the facility
     */
    public Facility getFacility() {
        lock.lock();
        try {
            return facility;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the facility used when calculating the priority of the message.
     *
     * @param facility the facility
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public void setFacility(final Facility facility) {
        checkAccess();
        lock.lock();
        try {
            this.facility = facility;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the host name which is used when sending the message to the syslog.
     *
     * @return the host name
     */
    public String getHostname() {
        lock.lock();
        try {
            return hostname;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the maximum length, in bytes, of the message allowed to be sent. The length includes the header and the
     * message.
     *
     * @return the maximum length, in bytes, of the message allowed to be sent
     */
    public int getMaxLength() {
        lock.lock();
        try {
            return maxLen;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the maximum length, in bytes, of the message allowed to tbe sent. Note that the message length includes the
     * header and the message itself.
     *
     * @param maxLen the maximum length, in bytes, allowed to be sent to the syslog server
     */
    public void setMaxLength(final int maxLen) {
        checkAccess();
        lock.lock();
        try {
            this.maxLen = maxLen;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the delimiter being used for the message if {@link #setUseMessageDelimiter(boolean) use message
     * delimiter} is set to {@code true}.
     *
     * @return the delimiter being used for the message
     */
    public String getMessageDelimiter() {
        checkAccess();
        lock.lock();
        try {
            return delimiter;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the message delimiter to be used if {@link #setUseMessageDelimiter(boolean) use message
     * delimiter} is set to {@code true}.
     *
     * @param delimiter the delimiter to use for the message
     */
    public void setMessageDelimiter(final String delimiter) {
        checkAccess();
        lock.lock();
        try {
            this.delimiter = delimiter;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks whether to append the message with a delimiter or not.
     *
     * @return {@code true} to append the message with a delimiter, otherwise {@code false}
     */
    public boolean isUseMessageDelimiter() {
        lock.lock();
        try {
            return useDelimiter;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether to append the message with a delimiter or not.
     *
     * @param useDelimiter {@code true} to append the message with a delimiter, otherwise {@code false}
     */
    public void setUseMessageDelimiter(final boolean useDelimiter) {
        checkAccess();
        lock.lock();
        try {
            this.useDelimiter = useDelimiter;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the host name which is used when sending the message to the syslog.
     * <p/>
     * This should be the name of the host sending the log messages, Note that the name cannot contain any whitespace.
     * <p/>
     * The hostname should be the most specific available value first. The order of preference for the contents of the
     * hostname is as follows:
     * <ol>
     * <li>FQDN</li>
     * <li>Static IP address</li>
     * <li>hostname</li>
     * <li>Dynamic IP address</li>
     * <li>{@code null}</li>
     * </ol>
     *
     * @param hostname the host name
     */
    public void setHostname(final String hostname) {
        checkAccess();
        lock.lock();
        try {
            this.hostname = checkPrintableAscii("host name", hostname);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns {@code true} if the message size should be prefixed to the message being sent.
     * <p/>
     * See <a href="http://tools.ietf.org/html/rfc6587#section-3.4.1">http://tools.ietf.org/html/rfc6587</a>
     * for more details on framing types.
     *
     * @return the message transfer type
     */
    public boolean isUseCountingFraming() {
        lock.lock();
        try {
            return useCountingFraming;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set to {@code true} if the message being sent should be prefixed with the size of the message.
     * <p/>
     * See <a href="http://tools.ietf.org/html/rfc6587#section-3.4.1">http://tools.ietf.org/html/rfc6587</a>
     * for more details on framing types.
     *
     * @param useCountingFraming {@code true} if the message being sent should be prefixed with the size of the message
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public void setUseCountingFraming(final boolean useCountingFraming) {
        checkAccess();
        lock.lock();
        try {
            this.useCountingFraming = useCountingFraming;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the server address the messages should be sent to.
     *
     * @param hostname the hostname used to created the connection
     *
     * @throws UnknownHostException if no IP address for the host could be found, or if a scope_id was specified for a
     *                              global IPv6 address.
     * @throws SecurityException    if a security manager exists and if the caller does not have {@code
     *                              LoggingPermission(control)}
     * @see InetAddress#getByName(String)
     */
    public void setServerHostname(final String hostname) throws UnknownHostException {
        checkAccess();
        setServerAddress(InetAddress.getByName(hostname));
    }

    /**
     * Returns the server address the messages are being sent to.
     *
     * @return the server address
     */
    public InetAddress getServerAddress() {
        lock.lock();
        try {
            return serverAddress;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the server address the messages should be sent to.
     *
     * @param serverAddress the server address
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public void setServerAddress(final InetAddress serverAddress) {
        checkAccess();
        lock.lock();
        try {
            this.serverAddress = serverAddress;
            initializeConnection = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the {@link SyslogType syslog type} this handler is using to format the message sent.
     *
     * @return the syslog type
     */
    public SyslogType getSyslogType() {
        lock.lock();
        try {
            return syslogType;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set the {@link SyslogType syslog type} this handler should use to format the message sent.
     *
     * @param syslogType the syslog type
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public void setSyslogType(final SyslogType syslogType) {
        checkAccess();
        lock.lock();
        try {
            this.syslogType = syslogType;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The protocol used to connect to the syslog server
     *
     * @return the protocol
     */
    public Protocol getProtocol() {
        lock.lock();
        try {
            return protocol;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the protocol used to connect to the syslog server
     *
     * @param type the protocol
     */
    public void setProtocol(final Protocol type) {
        checkAccess();
        lock.lock();
        try {
            this.protocol = type;
            initializeConnection = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the output stream for the syslog handler to write to.
     * <p/>
     * Setting the output stream closes any already established connections or open output streams and will not open
     * any new connections until the output stream is set to {@code null}. The {@link
     * #setProtocol(Protocol) protocol}, {@link
     * #setServerAddress(InetAddress), server address}, {@link #setServerHostname(String) server hostname} or
     * {@link #setPort(int) port} have no effect when the output stream is set.
     *
     * @param out the output stream to write to
     */
    public void setOutputStream(final OutputStream out) {
        checkAccess();
        setOutputStream(out, true);
    }

    /**
     * Checks if the message should truncated if the total length exceeds the {@link #getMaxLength() maximum length}.
     *
     * @return {@code true} if the message should be truncated if too large, otherwise {@code false}
     */
    public boolean isTruncate() {
        lock.lock();
        try {
            return truncate;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set to {@code true} if the message should be truncated if the total length the {@link #getMaxLength() maximum
     * length}.
     * <p/>
     * Set to {@code false} if the message should be split and sent as multiple messages. The header will remain the
     * same for each message sent. The wrapping is not a word based wrap and could split words between log messages.
     *
     * @param truncate {@code true} to truncate, otherwise {@code false} to send multiple messages
     */
    public void setTruncate(final boolean truncate) {
        checkAccess();
        lock.lock();
        try {
            this.truncate = truncate;
        } finally {
            lock.unlock();
        }
    }

    private void setOutputStream(final OutputStream out, final boolean outputStreamSet) {
        OutputStream oldOut = null;
        boolean ok = false;
        try {
            lock.lock();
            try {
                initializeConnection = false;
                oldOut = this.out;
                if (oldOut != null) {
                    safeFlush(oldOut);
                }
                this.out = out;
                ok = true;
                this.outputStreamSet = (out != null && outputStreamSet);
            } finally {
                lock.unlock();
            }
        } finally {
            safeClose(oldOut);
            if (!ok)
                safeClose(out);
        }
    }

    static void safeClose(final Closeable closeable) {
        if (closeable != null)
            try {
                closeable.close();
            } catch (Exception ignore) {
                // ignore
            }
    }

    static void safeFlush(final Flushable flushable) {
        if (flushable != null)
            try {
                flushable.flush();
            } catch (Exception ignore) {
                // ignore
            }
    }

    private void init() {
        if (initializeConnection && !outputStreamSet) {
            if (serverAddress == null || port < 0 || protocol == null) {
                throw new IllegalStateException(
                        "Invalid connection parameters. The port, server address and protocol must be set.");
            }
            initializeConnection = false;
            final OutputStream out;
            // Check the sockets
            try {
                final ClientSocketFactory clientSocketFactory = getClientSocketFactory();
                if (protocol == Protocol.UDP) {
                    out = new UdpOutputStream(clientSocketFactory);
                } else {
                    out = new TcpOutputStream(clientSocketFactory, blockOnReconnect);
                }
                setOutputStream(out, false);
            } catch (IOException e) {
                throw new IllegalStateException("Could not set " + protocol + " output stream.", e);
            }
        }
    }

    protected int calculatePriority(final Level level, final Facility facility) {
        final Severity severity = Severity.fromLevel(level);
        // facility * 8 + severity
        return facility.octal | severity.code;
    }

    private static final DateTimeFormatter RFC5424_DATE = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4)
            .appendLiteral('-')
            .appendValue(MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(DAY_OF_MONTH, 2)
            .appendLiteral('T')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .appendLiteral('.')
            .appendValue(MILLI_OF_SECOND, 3)
            .appendOffset("+HH:MM", "+00:00")
            .toFormatter();

    protected byte[] createRFC5424Header(final ExtLogRecord record) throws IOException {
        final ByteStringBuilder buffer = new ByteStringBuilder(256);
        // Set the property
        buffer.append('<').append(calculatePriority(record.getLevel(), facility)).append('>');
        // Set the version
        buffer.appendUSASCII("1 ");
        // Set the time
        RFC5424_DATE.formatTo(ZonedDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault()), buffer);
        buffer.append(' ');
        // Set the host name
        final String recordHostName = record.getHostName();
        if (hostname != null) {
            buffer.appendPrintUSASCII(hostname, 255).append(' ');
        } else if (recordHostName != null) {
            buffer.appendPrintUSASCII(recordHostName, 255).append(' ');
        } else {
            buffer.append(NILVALUE_SP);
        }
        // Set the app name
        final String recordProcName = record.getProcessName();
        if (appName != null) {
            buffer.appendPrintUSASCII(appName, 48);
            buffer.append(' ');
        } else if (recordProcName != null) {
            buffer.appendPrintUSASCII(recordProcName, 48);
            buffer.append(' ');
        } else {
            buffer.appendUSASCII(NILVALUE_SP);
        }
        // Set the procid
        final long recordProcId = record.getProcessId();
        if (recordProcId != -1) {
            buffer.append(recordProcId);
            buffer.append(' ');
        } else {
            buffer.appendUSASCII(NILVALUE_SP);
        }
        // Set the msgid
        final String msgid = record.getLoggerName();
        if (msgid == null) {
            buffer.appendUSASCII(NILVALUE_SP);
        } else if (msgid.isEmpty()) {
            buffer.appendUSASCII("root-logger");
            buffer.append(' ');
        } else {
            buffer.appendPrintUSASCII(msgid, 32);
            buffer.append(' ');
        }
        // Set the structured data
        buffer.appendUSASCII(NILVALUE_SP);
        // TODO (jrp) review structured data http://tools.ietf.org/html/rfc5424#section-6.3
        final String encoding = getEncoding();
        if (encoding == null || DEFAULT_ENCODING.equalsIgnoreCase(encoding)) {
            buffer.appendUtf8Raw(0xFEFF);
        }
        return buffer.toArray();
    }

    private static final DateTimeFormatter RFC3164_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendText(MONTH_OF_YEAR, TextStyle.SHORT)
            .appendLiteral(' ')
            .padNext(2)
            .appendValue(DAY_OF_MONTH)
            .appendLiteral(' ')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .toFormatter();

    protected byte[] createRFC3164Header(final ExtLogRecord record) throws IOException {
        final ByteStringBuilder buffer = new ByteStringBuilder(256);
        // Set the property
        buffer.append('<').append(calculatePriority(record.getLevel(), facility)).append('>');

        // Set the time
        RFC3164_DATE.formatTo(ZonedDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault()), buffer);
        buffer.append(' ');

        // Set the host name
        final String recordHostName = record.getHostName();
        if (hostname != null) {
            buffer.appendUSASCII(hostname).append(' ');
        } else if (recordHostName != null) {
            buffer.appendUSASCII(recordHostName).append(' ');
        } else {
            buffer.appendUSASCII("UNKNOWN_HOSTNAME").append(' ');
        }
        // Set the app name and the proc id
        final String recordProcName = record.getProcessName();
        boolean colon = false;
        if (appName != null) {
            buffer.appendUSASCII(appName);
            colon = true;
        } else if (recordProcName != null) {
            buffer.appendUSASCII(recordProcName);
            colon = true;
        }
        final long recordProcId = record.getProcessId();
        if (recordProcId != -1) {
            buffer.append('[').append(recordProcId).append(']');
            colon = true;
        }
        if (colon) {
            buffer.append(':').append(' ');
        }
        return buffer.toArray();
    }

    private ClientSocketFactory getClientSocketFactory() {
        lock.lock();
        try {
            if (clientSocketFactory != null) {
                return clientSocketFactory;
            }
            final SocketFactory socketFactory = (protocol == Protocol.SSL_TCP ? SSLSocketFactory.getDefault()
                    : SocketFactory.getDefault());
            return ClientSocketFactory.of(socketFactory, serverAddress, port);
        } finally {
            lock.unlock();
        }
    }

    private static String checkPrintableAscii(final String name, final String value) {
        if (value != null && PRINTABLE_ASCII_PATTERN.matcher(value).find()) {
            final String upper = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            throw new IllegalArgumentException(String.format(
                    "%s '%s' is invalid. The %s must be printable ASCII characters with no spaces.", upper, value, name));
        }
        return value;
    }
}
