/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.logmanager.handlers;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
// TODO (jrp) write better JavaDoc

/**
 * A syslog handler for logging to syslogd.
 * <p/>
 * Log messages can be sent via {@link Protocol#TCP TCP}, {@link Protocol#SSL_TCP SSL} or {@link Protocol#UDP UDP} with
 * {@link Protocol#UDP UDP} being the default.
 * <p/>
 * TODO (jrp) remove these defaults from the JavaDoc
 * <p/>
 * this.serverAddress = serverAddress;
 * this.port = port;
 * this.facility = facility;
 * this.pid = findPid();
 * this.appName = "java";
 * this.hostname = hostname;
 * this.syslogType = (syslogType == null ? SyslogType.RFC5424 : syslogType);
 * if (protocol == null) {
 * this.protocol = Protocol.UDP;
 * delimiter = null;
 * useDelimiter = false;
 * } else if (protocol == Protocol.UDP) {
 * delimiter = null;
 * useDelimiter = false;
 * } else if (protocol == Protocol.TCP || protocol == Protocol.SSL_TCP) {
 * delimiter = "\n";
 * useDelimiter = true;
 * }
 * this.messageTransfer = MessageTransfer.NON_TRANSPARENT_FRAMING;
 * initializeConnection = true;
 * outputStreamSet = false;
 * escapeEnabled = true;
 * truncate = true;
 * if (this.syslogType == SyslogType.RFC3164) {
 * maxLen = 1024;
 * } else if (this.syslogType == SyslogType.RFC5424) {
 * maxLen = 20448;
 * }
 * <p/>
 * <p/>
 * The log messages are sent via UDP and formatted based on the {@link SyslogType}. Note that not all settable fields
 * are used on all format types.
 * <p/>
 * Setting {@link #setFormatter(java.util.logging.Formatter) formatter} has no effect on this handler. The syslog RFC
 * specifications require explicit message formats.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
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
     * The type of framing that should be used for TCP connections.
     */
    public static enum MessageTransfer {
        /**
         * Used to indicate messages should be prefixed with the total message length to act as a delimiter for the
         * syslog reliever. Not all syslog servers support this.
         */
        OCTET_COUNTING,

        /**
         * Used to indicate a traditional delimiter should be used. The delimiter should be specified via the {@link
         * #setFormatter(java.util.logging.Formatter) formatter}.
         * <p/>
         * LF terminator example:
         * <pre>
         *     <code>
         *
         *     final {@link org.jboss.logmanager.formatters.PatternFormatter PatternFormatter} formatter = new {@link
         * org.jboss.logmanager.formatters.PatternFormatter PatternFormatter()};
         *     formatter.{@link org.jboss.logmanager.formatters.PatternFormatter#setPattern(String)
         * setPattern("%s%n")};
         *     syslogHandler.{@link #setFormatter(java.util.logging.Formatter) formatter(formatter)};
         *     </code>
         * </pre>
         * <p/>
         * This only disables the {@link #OCTET_COUNTING}.
         */
        NON_TRANSPARENT_FRAMING,
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
            } else if (levelValue >= org.jboss.logmanager.Level.SEVERE.intValue() || levelValue >= org.jboss.logmanager.Level.ERROR.intValue()) {
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
         * Formats the message according the the RFC-5424 specification (<a href="http://tools.ietf.org/html/rfc5424#section-6">http://tools.ietf.org/html/rfc5424#section-6</a>
         */
        RFC5424,

        /**
         * Formats the message according the the RFC-3164 specification (<a href="http://tools.ietf.org/html/rfc3164#section-4.1">http://tools.ietf.org/html/rfc3164#section-4.1</a>
         */
        RFC3164,
    }

    public static final InetAddress DEFAULT_ADDRESS;
    public static final int DEFAULT_PORT = 514;
    public static final int DEFAULT_SECURE_PORT = 6514;
    public static final String DEFAULT_ENCODING = "UTF-8";
    public static final Facility DEFAULT_FACILITY = Facility.USER_LEVEL;
    public static final String NILVALUE_SP = "- ";

    static {
        try {
            DEFAULT_ADDRESS = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Could not create address to localhost");
        }
    }

    private static final String UTF_16_BOM = new String(new char[] {(char) 0xfeff});

    private final Object outputLock = new Object();
    private InetAddress serverAddress;
    private int port;
    private String appName;
    private String hostname;
    private volatile Facility facility;
    private SyslogType syslogType;
    private final String pid;
    private OutputStream out;
    private Protocol protocol;
    private MessageTransfer messageTransfer;
    private boolean initializeConnection;
    private boolean outputStreamSet;
    private byte[] bom;
    private String delimiter;
    private boolean useDelimiter;
    private boolean escapeEnabled;
    private boolean truncate;
    private int maxLen;

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
    public SyslogHandler(final String serverHostname, final int port, final Facility facility, final String hostname) throws IOException {
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
    public SyslogHandler(final InetAddress serverAddress, final int port, final Facility facility, final String hostname) throws IOException {
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
    public SyslogHandler(final String serverHostname, final int port, final Facility facility, final SyslogType syslogType, final String hostname) throws IOException {
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
    public SyslogHandler(final InetAddress serverAddress, final int port, final Facility facility, final SyslogType syslogType, final String hostname) throws IOException {
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
    public SyslogHandler(final String serverHostname, final int port, final Facility facility, final SyslogType syslogType, final Protocol protocol, final String hostname) throws IOException {
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
    public SyslogHandler(final InetAddress serverAddress, final int port, final Facility facility, final SyslogType syslogType, final Protocol protocol, final String hostname) throws IOException {
        this.serverAddress = serverAddress;
        this.port = port;
        this.facility = facility;
        this.pid = findPid();
        this.appName = "java";
        this.hostname = hostname;
        this.syslogType = (syslogType == null ? SyslogType.RFC5424 : syslogType);
        if (protocol == null) {
            this.protocol = Protocol.UDP;
            delimiter = null;
            useDelimiter = false;
        } else if (protocol == Protocol.UDP) {
            delimiter = null;
            useDelimiter = false;
        } else if (protocol == Protocol.TCP || protocol == Protocol.SSL_TCP) {
            delimiter = "\n";
            useDelimiter = true;
        }
        this.messageTransfer = MessageTransfer.NON_TRANSPARENT_FRAMING;
        initializeConnection = true;
        outputStreamSet = false;
        escapeEnabled = true;
        truncate = true;
        if (this.syslogType == SyslogType.RFC3164) {
            maxLen = 1024;
        } else if (this.syslogType == SyslogType.RFC5424) {
            maxLen = 2048;
        }
    }

    @Override
    public final void doPublish(final ExtLogRecord record) {
        // Don't log empty messages
        if (record.getMessage() == null || record.getMessage().isEmpty()) {
            return;
        }
        synchronized (outputLock) {
            init();
            if (out == null) {
                throw new IllegalStateException("The syslog handler has been closed.");
            }
            try {
                final ByteOutputStream headerBuffer = new ByteOutputStream();

                switch (syslogType) {
                    case RFC5424:
                        writeRFC5424Header(headerBuffer, record);
                        break;
                    case RFC3164:
                        writeRFC3164Header(headerBuffer, record);
                        break;
                }

                // Set the message
                final Formatter formatter = getFormatter();
                String logMsg;
                if (formatter != null) {
                    logMsg = formatter.format(record);
                } else {
                    logMsg = record.getFormattedMessage();
                }
                if (escapeEnabled) {
                    logMsg = escape(logMsg);
                }

                // TODO (jrp) the BOM doesn't seem to work with rsyslog unless it's UTF-8
                final ByteOutputStream messageBuffer = new ByteOutputStream();
                final String encoding = getEncoding();
                if (encoding == null) {
                    messageBuffer.writeString(logMsg, DEFAULT_ENCODING);
                } else {
                    messageBuffer.writeString(logMsg, encoding);
                }

                // Header bytes
                final byte[] header = headerBuffer.toByteArray();

                final byte[] msg = messageBuffer.toByteArray();

                if ((header.length + msg.length) > maxLen) {
                    final int maxMsgLen = maxLen - header.length;
                    // The full header must be there, if the header is greater than the maxLen report an exception
                    if (maxMsgLen < 1) {
                        throw new IOException(String.format("The header length, %d, is great than the message length, %d, allows.", header.length, maxLen));
                    }
                    final ByteOutputStream payload = new ByteOutputStream();
                    if (truncate) {
                        // Should the message size be inserted
                        if (messageTransfer == MessageTransfer.OCTET_COUNTING) {
                            payload.writeInt(maxLen);
                            payload.write(' ');
                        }
                        payload.write(header);
                        if (bom != null) {
                            payload.write(bom);
                        }
                        payload.write(msg, 0, maxMsgLen);
                        if (useDelimiter) {
                            payload.writeString(delimiter);
                        }
                        this.out.write(payload.toByteArray());
                    } else {
                        final int headerLen = header.length;
                        int offset = 0;
                        int len = maxMsgLen;
                        int remaining = msg.length;
                        while (remaining > 0) {
                            // Should the message size be inserted
                            if (messageTransfer == MessageTransfer.OCTET_COUNTING) {
                                payload.writeInt(headerLen + len);
                                payload.write(' ');
                            }
                            payload.write(header);
                            if (bom != null) {
                                payload.write(bom);
                            }
                            payload.write(msg, offset, len);

                            // Write the delimiter if required
                            if (useDelimiter) {
                                payload.writeString(delimiter);
                            }

                            // Write to the socket
                            this.out.write(payload.toByteArray());

                            // Reset the payload
                            payload.reset();
                            offset = offset + len;
                            remaining -= len;
                            len = Math.min(maxMsgLen, remaining);
                        }

                    }
                } else {
                    final ByteOutputStream payload = new ByteOutputStream();

                    // Should the message size be inserted
                    if (messageTransfer == MessageTransfer.OCTET_COUNTING) {
                        payload.writeInt(header.length + msg.length);
                        payload.write(' ');
                    }
                    payload.write(header);
                    if (bom != null) {
                        payload.write(bom);
                    }
                    payload.write(msg);
                    if (useDelimiter) {
                        payload.writeString(delimiter);
                    }
                    this.out.write(payload.toByteArray());
                }
            } catch (IOException e) {
                reportError("Could not write to syslog", e, ErrorManager.WRITE_FAILURE);
            }
        }
        super.doPublish(record);
    }

    @Override
    public void setEncoding(final String encoding) throws SecurityException, UnsupportedEncodingException {
        super.setEncoding(encoding);
        if (encoding == null) {
            bom = null;
        } else {
            bom = UTF_16_BOM.getBytes(encoding);
        }
    }

    @Override
    public void close() {
        synchronized (outputLock) {
            safeClose(out);
            out = null;
        }
        super.close();
    }

    @Override
    public void flush() {
        synchronized (outputLock) {
            safeFlush(out);
        }
        super.flush();
    }

    /**
     * Gets app name used when formatting the message in RFC5424 format. By default the app name is &quot;java&quot;
     *
     * @return the app name being used
     */
    public String getAppName() {
        synchronized (outputLock) {
            return appName;
        }
    }

    /**
     * Sets app name used when formatting the message in RFC5424 format. By default the app name is &quot;java&quot;
     *
     * @param appName the app name to use
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}
     */
    public void setAppName(final String appName) {
        checkAccess(this);
        synchronized (outputLock) {
            this.appName = appName;
        }
    }

    /**
     * Checks whether or not characters below decimal 32, traditional US-ASCII control values expect {@code DEL}, are
     * being escaped or not.
     *
     * @return {@code true} if characters are being expected in a {@code #xxx} pattern where {@code xxx} is the octal
     *         representation of the value.
     */
    public boolean isEscapeEnabled() {
        return escapeEnabled;
    }

    /**
     * Set to {@code true} to escape characters within the message string that are below decimal 32. These values are
     * tradition US-ASCII control values. The values will be replaced in a {@code #xxx} format where {@code xxx} is the
     * octal value of the character being replaced.
     *
     * @param escapeEnabled {@code true} to escape characters, {@code false} to not escape characters
     */
    public void setEscapeEnabled(final boolean escapeEnabled) {
        this.escapeEnabled = escapeEnabled;
    }

    /**
     * Returns the pid being used as the PROCID for RFC5424 messages.
     *
     * @return the pid
     */
    public String getPid() {
        return pid;
    }

    /**
     * Returns the port the syslogd is listening on.
     *
     * @return the port
     */
    public int getPort() {
        synchronized (outputLock) {
            return port;
        }
    }

    /**
     * Sets the port the syslogd server is listening on.
     *
     * @param port the port
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}
     */
    public void setPort(final int port) {
        checkAccess(this);
        synchronized (outputLock) {
            this.port = port;
            initializeConnection = true;
        }
    }

    /**
     * Returns the facility used for calculating the priority of the message.
     *
     * @return the facility
     */
    public Facility getFacility() {
        return facility;
    }

    /**
     * Sets the facility used when calculating the priority of the message.
     *
     * @param facility the facility
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}
     */
    public void setFacility(final Facility facility) {
        checkAccess(this);
        synchronized (outputLock) {
            this.facility = facility;
        }
    }

    /**
     * Returns the host name which is used when sending the message to the syslog.
     *
     * @return the host name
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Returns the maximum length, in bytes, of the message allowed to be sent. The length includes the header and the
     * message.
     *
     * @return the maximum length, in bytes, of the message allowed to be sent
     */
    public int getMaxLength() {
        return maxLen;
    }

    /**
     * Sets the maximum length, in bytes, of the message allowed to tbe sent. Note that the message length includes the
     * header and the message itself.
     *
     * @param maxLen the maximum length, in bytes, allowed to be sent to the syslog server
     */
    public void setMaxLength(final int maxLen) {
        this.maxLen = maxLen;
    }

    /**
     * Returns the delimiter being used for the message if {@link #setUseMessageDelimiter(boolean) use message
     * delimiter} is set to {@code true}.
     *
     * @return the delimiter being used for the message
     */
    public String getMessageDelimiter() {
        checkAccess(this);
        synchronized (outputLock) {
            return delimiter;
        }
    }

    /**
     * Sets the message delimiter to be used if {@link #setUseMessageDelimiter(boolean) use message
     * delimiter} is set to {@code true}.
     *
     * @param delimiter the delimiter to use for the message
     */
    public void setMessageDelimiter(final String delimiter) {
        checkAccess(this);
        synchronized (outputLock) {
            this.delimiter = delimiter;
        }
    }

    /**
     * Checks whether to append the message with a delimiter or not.
     *
     * @return {@code true} to append the message with a delimiter, otherwise {@code false}
     */
    public boolean isUseMessageDelimiter() {
        return useDelimiter;
    }

    /**
     * Whether to append the message with a delimiter or not.
     *
     * @param useDelimiter {@code true} to append the message with a delimiter, otherwise {@code false}
     */
    public void setUseMessageDelimiter(final boolean useDelimiter) {
        checkAccess(this);
        synchronized (outputLock) {
            this.useDelimiter = useDelimiter;
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
        if (hostname != null && hostname.contains(" ")) {
            throw new IllegalArgumentException(String.format("Host name '%s' is invalid. Whitespace is now allowed in the host name.", hostname));
        }
        synchronized (outputLock) {
            this.hostname = hostname;
        }
    }

    /**
     * Returns the message transfer type.
     *
     * @return the message transfer type
     */
    public MessageTransfer getMessageTransfer() {
        return messageTransfer;
    }

    /**
     * Sets the message transfer type.
     *
     * @param messageTransfer the message transfer type
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}
     */
    public void setMessageTransfer(final MessageTransfer messageTransfer) {
        checkAccess(this);
        synchronized (outputLock) {
            this.messageTransfer = messageTransfer;
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
     *                              LoggingPermission(control)} or the handler is {@link #protect(Object) protected}
     * @see InetAddress#getByName(String)
     */
    public void setServerHostname(final String hostname) throws UnknownHostException {
        checkAccess(this);
        setServerAddress(InetAddress.getByName(hostname));
    }

    /**
     * Returns the server address the messages are being sent to.
     *
     * @return the server address
     */
    public InetAddress getServerAddress() {
        synchronized (outputLock) {
            return serverAddress;
        }
    }

    /**
     * Sets the server address the messages should be sent to.
     *
     * @param serverAddress the server address
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}
     */
    public void setServerAddress(final InetAddress serverAddress) {
        checkAccess(this);
        synchronized (outputLock) {
            this.serverAddress = serverAddress;
            initializeConnection = true;
        }
    }

    /**
     * Returns the {@link SyslogType syslog type} this handler is using to format the message sent.
     *
     * @return the syslog type
     */
    public SyslogType getSyslogType() {
        synchronized (outputLock) {
            return syslogType;
        }
    }

    /**
     * Set the {@link SyslogType syslog type} this handler should use to format the message sent.
     *
     * @param syslogType the syslog type
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}
     */
    public void setSyslogType(final SyslogType syslogType) {
        checkAccess(this);
        synchronized (outputLock) {
            this.syslogType = syslogType;
        }
    }

    /**
     * The protocol used to connect to the syslog server
     *
     * @return the protocol
     */
    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * Sets the protocol used to connect to the syslog server
     *
     * @param type the protocol
     */
    public void setProtocol(final Protocol type) {
        checkAccess(this);
        synchronized (outputLock) {
            this.protocol = type;
            initializeConnection = true;
        }
    }

    /**
     * Sets the output stream for the syslog handler to write to.
     * <p/>
     * Setting the output stream closes any already established connections or open output streams and will not open
     * any new connections until the output stream is set to {@code null}. The {@link
     * #setProtocol(org.jboss.logmanager.handlers.SyslogHandler.Protocol) protocol}, {@link
     * #setServerAddress(java.net.InetAddress), server address}, {@link #setServerHostname(String) server hostname} or
     * {@link #setPort(int) port} have no effect when the output stream is set.
     *
     * @param out the output stream to write to
     */
    public void setOutputStream(final OutputStream out) {
        setOutputStream(out, true);
    }

    /**
     * Checks if the message should truncated if the total length, not including the {@link
     * MessageTransfer#OCTET_COUNTING octet-counting} size, exceeds the {@link #getMaxLength() maximum length}.
     *
     * @return {@code true} if the message should be truncated if too large, otherwise {@code false}
     */
    public boolean isTruncate() {
        return truncate;
    }

    /**
     * Set to {@code true} if the message should be truncated if the total length, not including the {@link
     * MessageTransfer#OCTET_COUNTING octet-counting} size, exceeds the {@link #getMaxLength() maximum length}.
     * <p/>
     * Set to {@code false} if the message should be split and sent as multiple messages. The header will remain the
     * same for each message sent. The wrapping is not a word based wrap and could split words between log messages.
     *
     * @param truncate {@code true} to truncate, otherwise {@code false} to send multiple messages
     */
    public void setTruncate(final boolean truncate) {
        this.truncate = truncate;
    }

    private void setOutputStream(final OutputStream out, final boolean outputStreamSet) {
        checkAccess(this);
        OutputStream oldOut = null;
        boolean ok = false;
        try {
            synchronized (outputLock) {
                initializeConnection = false;
                oldOut = this.out;
                if (oldOut != null) {
                    safeFlush(oldOut);
                }
                this.out = out;
                ok = true;
                this.outputStreamSet = (out != null && outputStreamSet);
            }
        } finally {
            safeClose(oldOut);
            if (!ok) safeClose(out);
        }
    }

    static void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (Exception ignore) {
            // ignore
        }
    }

    static void safeFlush(final Flushable flushable) {
        if (flushable != null) try {
            flushable.flush();
        } catch (Exception ignore) {
            // ignore
        }
    }

    private static String findPid() {
        final String name = ManagementFactory.getRuntimeMXBean().getName();
        String result = name;
        final int index = name.indexOf("@");
        if (index > -1) {
            try {
                result = Integer.toString(Integer.valueOf(name.substring(0, index - 1)));
            } catch (NumberFormatException ignore) {
                // ignore
            }
        }
        return result;
    }

    private void init() {
        if (initializeConnection && !outputStreamSet) {
            if (serverAddress == null || port < 0 || protocol == null) {
                throw new IllegalStateException("Invalid connection parameters. The port, server address and protocol must be set.");
            }
            initializeConnection = false;
            // Check the sockets
            switch (protocol) {
                case TCP: {
                    try {
                        setOutputStream(new TcpOutputStream(serverAddress, port), false);
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not set TCP output stream.", e);
                    }
                    break;
                }
                case UDP: {
                    try {
                        setOutputStream(new UdpOutputStream(serverAddress, port), false);
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not set UDP output stream.", e);
                    }
                    break;
                }
                case SSL_TCP:
                    try {
                        setOutputStream(new SslTcpOutputStream(serverAddress, port), false);
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not set SSL output stream.", e);
                    }
                    break;
            }
        }
    }

    protected int calculatePriority(final Level level, final Facility facility) {
        final Severity severity = Severity.fromLevel(level);
        // facility * 8 + severity
        return facility.octal | severity.code;
    }

    protected void writeRFC5424Header(final ByteOutputStream buffer, final ExtLogRecord record) throws IOException {
        // Set the property
        buffer.writeString("<").writeInt(calculatePriority(record.getLevel(), facility)).writeString(">");
        // Set the version
        buffer.writeString("1 ");
        // Set the time
        final long millis = record.getMillis();
        if (millis <= 0) {
            buffer.writeString(NILVALUE_SP);
        } else {
            // The follow can be changed to use a formatter with Java 7 pattern is yyyy-MM-dd'T'hh:mm:ss.SSSXXX
            final Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(millis);
            final int month = cal.get(Calendar.MONTH);
            final int day = cal.get(Calendar.DAY_OF_MONTH);
            final int hours = cal.get(Calendar.HOUR_OF_DAY);
            final int minutes = cal.get(Calendar.MINUTE);
            final int seconds = cal.get(Calendar.SECOND);
            buffer.writeInt(cal.get(Calendar.YEAR)).writeChar('-');
            if (month < 10) {
                buffer.writeInt(0);
            }
            buffer.writeInt(month + 1).writeChar('-');
            if (day < 10) {
                buffer.writeInt(0);
            }
            buffer.writeInt(day).writeChar('T');
            if (hours < 10) {
                buffer.writeInt(0);
            }
            buffer.writeInt(hours).writeChar(':');
            if (minutes < 10) {
                buffer.writeInt(0);
            }
            buffer.writeInt(minutes).writeChar(':');
            if (seconds < 10) {
                buffer.writeInt(0);
            }
            buffer.writeInt(seconds).writeChar('.');
            final int milliseconds = cal.get(Calendar.MILLISECOND);
            if (milliseconds < 10) {
                buffer.writeInt(0).writeInt(0);
            } else if (milliseconds < 100) {
                buffer.writeInt(0);
            }
            buffer.writeInt(milliseconds);
            final int tz = cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET);
            if (tz == 0) {
                buffer.writeString("+00:00");
            } else {
                int tzMinutes = tz / 60000; // milliseconds to minutes
                if (tzMinutes < 0) {
                    tzMinutes = -tzMinutes;
                    buffer.writeChar('-');
                } else {
                    buffer.writeChar('+');
                }
                final int tzHour = tzMinutes / 60; // minutes to hours
                tzMinutes -= tzHour * 60; // subtract hours from minutes in minutes
                if (tzHour < 10) {
                    buffer.writeInt(0);
                }
                buffer.writeInt(tzHour).writeChar(':');
                if (tzMinutes < 10) {
                    buffer.writeInt(0);
                }
                buffer.writeInt(tzMinutes);
            }
            buffer.writeString(" ");
        }
        // Set the host name
        if (hostname == null) {
            buffer.writeString(NILVALUE_SP);
        } else {
            buffer.writeUSASCII(hostname, 255).writeString(" ");
        }
        // Set the app name
        if (appName == null) {
            buffer.writeString(NILVALUE_SP);
        } else {
            buffer.writeUSASCII(appName, 48);
            buffer.writeString(" ");
        }
        // Set the procid
        if (pid == null) {
            buffer.writeString(NILVALUE_SP);
        } else {
            buffer.writeUSASCII(pid, 128);
            buffer.writeString(" ");
        }
        // Set the msgid
        final String msgid = record.getLoggerName();
        if (msgid == null) {
            buffer.writeString(NILVALUE_SP);
        } else if (msgid.isEmpty()) {
            buffer.writeUSASCII("root-logger");
            buffer.writeString(" ");
        } else {
            buffer.writeUSASCII(msgid, 32);
            buffer.writeString(" ");
        }
        // Set the structured data
        buffer.writeString(NILVALUE_SP);
        // TODO (jrp) review structured data http://tools.ietf.org/html/rfc5424#section-6.3
    }

    protected void writeRFC3164Header(final ByteOutputStream buffer, final ExtLogRecord record) throws IOException {
        // Set the property
        buffer.writeChar('<').writeInt(calculatePriority(record.getLevel(), facility)).writeChar('>');

        // Set the time
        final long millis = record.getMillis();
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis((millis <= 0 ? System.currentTimeMillis() : millis));
        final int month = cal.get(Calendar.MONTH);
        final int day = cal.get(Calendar.DAY_OF_MONTH);
        final int hours = cal.get(Calendar.HOUR_OF_DAY);
        final int minutes = cal.get(Calendar.MINUTE);
        final int seconds = cal.get(Calendar.SECOND);
        final DateFormatSymbols formatSymbols = DateFormatSymbols.getInstance(Locale.ENGLISH);
        buffer.writeString(formatSymbols.getShortMonths()[month]).writeString(" ");
        if (day < 10) {
            buffer.writeString(" ");
        }
        buffer.writeInt(day).writeString(" ");
        if (hours < 10) {
            buffer.writeInt(0);
        }
        buffer.writeInt(hours).writeChar(':');
        if (minutes < 10) {
            buffer.writeInt(0);
        }
        buffer.writeInt(minutes).writeChar(':');
        if (seconds < 10) {
            buffer.writeInt(0);
        }
        buffer.writeInt(seconds);
        buffer.writeString(" ");

        // Set the host name
        if (hostname == null) {
            // TODO might not be the best solution
            buffer.writeString("UNKNOWN_HOSTNAME").writeString(" ");
        } else {
            buffer.writeString(hostname).writeString(" ");
        }
        // Set the app name and the proc id
        if (appName != null && pid != null) {
            buffer.writeString(appName).writeString("[").writeString(pid).writeString("]").writeString(": ");
        } else if (appName != null) {
            buffer.writeString(appName).writeString(": ");
        } else if (pid != null) {
            buffer.writeString("[").writeString(pid).writeString("]").writeString(": ");
        }
    }

    private static String escape(final String s) {
        final StringBuilder result = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 0 && c < 32) {
                result.append('#');
                if (c < 8) {
                    result.append('0').append('0').append((int) c);
                } else {
                    result.append('0').append(c >> 3).append(c & 0b111);
                }
            } else {
                result.append(c);
            }
        }
        return result.toString().trim();
    }

    static class ByteOutputStream extends ByteArrayOutputStream {

        public ByteOutputStream writeUSASCII(final String s, int maxLen) throws IOException {
            final byte[] bytes = s.getBytes("US-ASCII");
            write(bytes, 0, Math.min(maxLen, bytes.length));
            return this;
        }

        public ByteOutputStream writeUSASCII(final String s) throws IOException {
            write(s.getBytes("US-ASCII"));
            return this;
        }

        public ByteOutputStream writeString(final String s, final String encoding) throws IOException {
            write(s.getBytes(encoding));
            return this;
        }

        public ByteOutputStream writeString(final String s, int maxLen) throws IOException {
            final byte[] bytes = s.getBytes();
            write(bytes, 0, Math.min(maxLen, bytes.length));
            return this;
        }

        public ByteOutputStream writeString(final String s) throws IOException {
            write(s.getBytes());
            return this;
        }

        public ByteOutputStream writeInt(final int i) throws IOException {
            return writeString(Integer.toString(i));
        }

        public ByteOutputStream writeChar(final char c) throws IOException {
            if (c >= 0 && c <= 0x7f) {
                write((byte) c);
            } else if (c <= 0x07ff) {
                write((byte) (0xc0 | 0x1f & c >> 6));
                write((byte) (0x80 | 0x3f & c));
            } else {
                write((byte) (0xe0 | 0x0f & c >> 12));
                write((byte) (0x80 | 0x3f & c >> 6));
                write((byte) (0x80 | 0x3f & c));
            }
            return this;
        }
    }
}
