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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.DateFormatSymbols;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;

/**
 * A syslog handler for logging to syslogd.
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
        RFC5424 {
            @Override
            protected byte[] format(final ExtLogRecord record, final Facility facility, final String hostname, final String pid, final String appName) throws UnsupportedEncodingException {
                final StringBuilder message = new StringBuilder();
                // Set the property
                message.append('<').append(calculatePriority(record.getLevel(), facility)).append('>');
                // Set the version
                message.append("1 ");
                // Set the time
                final long millis = record.getMillis();
                if (millis <= 0) {
                    message.append(NILVALUE).append(' ');
                } else {
                    formatDate(this, millis, message);
                }
                message.append(' ');
                // Set the host name
                if (hostname == null) {
                    message.append(NILVALUE).append(' ');
                } else {
                    message.append(hostname).append(' ');
                }
                // Set the app name
                if (appName == null) {
                    message.append(NILVALUE).append(' ');
                } else {
                    message.append(appName).append(' ');
                }
                // Set the procid
                if (pid == null) {
                    message.append(NILVALUE).append(' ');
                } else {
                    message.append(pid).append(' ');
                }
                // Set the msgid
                final String msgid = record.getLoggerName();
                if (msgid == null || msgid.isEmpty()) {
                    message.append(NILVALUE).append(' ');
                } else {
                    message.append(msgid).append(' ');
                }
                // Set the structured data
                message.append(NILVALUE).append(' ');
                final ByteArrayOutputStream result = new ByteArrayOutputStream();
                try {
                    result.write(message.toString().getBytes());
                    result.write(record.getFormattedMessage().getBytes(ENCODING));
                } catch (IOException e) {
                    throw new IllegalStateException("Could not write syslog message", e);
                }
                return result.toByteArray();
            }
        },

        /**
         * Formats the message according the the RFC-3164 specification (<a href="http://tools.ietf.org/html/rfc3164#section-4.1">http://tools.ietf.org/html/rfc3164#section-4.1</a>
         */
        RFC3164 {
            @Override
            protected byte[] format(final ExtLogRecord record, final Facility facility, final String hostname, final String pid, final String appName) throws UnsupportedEncodingException {
                final StringBuilder message = new StringBuilder();
                // Set the property
                message.append('<').append(calculatePriority(record.getLevel(), facility)).append('>');
                // Set the time
                final long millis = record.getMillis();
                formatDate(this, (millis <= 0 ? System.currentTimeMillis() : millis), message);
                message.append(' ');
                // Set the host name
                if (hostname == null) {
                    // TODO might not be the best solution
                    message.append("UNKNOWN_HOSTNAME").append(' ');
                } else {
                    message.append(hostname).append(' ');
                }
                // Set the app name and the proc id
                if (appName != null && pid != null) {
                    message.append(appName).append("[").append(pid).append("]").append(": ");
                } else if (appName != null) {
                    message.append(appName).append(": ");
                } else if (pid != null) {
                    message.append("[").append(pid).append("]").append(": ");
                }
                // Set the message
                // The encoding is not specified in the spec, but UTF-8 is probably the safest bet
                message.append(new String(record.getFormattedMessage().getBytes(ENCODING), ENCODING));
                final CharBuffer in = CharBuffer.wrap(message);
                final ByteBuffer out = ByteBuffer.allocate(1024);
                final CharsetEncoder encoder = Charset.forName(ENCODING).newEncoder();
                encoder.encode(in, out, true);
                encoder.flush(out);
                if (out.hasArray()) {
                    return Arrays.copyOf(out.array(), out.position());
                }

                final byte[] result = new byte[out.position()];
                out.flip();
                for (int i = 0; i < result.length; i++) {
                    result[i] = out.get();
                }
                return result;
            }
        };

        protected abstract byte[] format(ExtLogRecord record, Facility facility, String hostname, String pid, String appName) throws UnsupportedEncodingException;

        protected static int calculatePriority(final Level level, final Facility facility) {
            final Severity severity = Severity.fromLevel(level);
            // facility * 8 + severity
            return facility.octal | severity.code;
        }

        protected static void formatDate(final SyslogType syslogType, final long millis, final StringBuilder buffer) {
            final Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(millis);
            final int month = cal.get(Calendar.MONTH);
            final int day = cal.get(Calendar.DAY_OF_MONTH);
            final int hours = cal.get(Calendar.HOUR_OF_DAY);
            final int minutes = cal.get(Calendar.MINUTE);
            final int seconds = cal.get(Calendar.SECOND);

            switch (syslogType) {
                // yyyy-MM-dd'T'HH:mm:ss.SSSXXX
                case RFC5424: {
                    buffer.append(cal.get(Calendar.YEAR)).append('-');
                    if (month < 10) {
                        buffer.append(0);
                    }
                    buffer.append(month + 1).append('-');
                    if (day < 10) {
                        buffer.append(0);
                    }
                    buffer.append(day).append('T');
                    if (hours < 10) {
                        buffer.append(0);
                    }
                    buffer.append(hours).append(':');
                    if (minutes < 10) {
                        buffer.append(0);
                    }
                    buffer.append(minutes).append(':');
                    if (seconds < 10) {
                        buffer.append(0);
                    }
                    buffer.append(seconds).append('.');
                    final int milliseconds = cal.get(Calendar.MILLISECOND);
                    if (milliseconds < 10) {
                        buffer.append(0).append(0);
                    } else if (milliseconds < 100) {
                        buffer.append(0);
                    }
                    buffer.append(milliseconds);
                    final int tz = cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET);
                    if (tz == 0) {
                        buffer.append("+00:00");
                    } else {
                        int tzMinutes = tz / 60000; // milliseconds to minutes
                        if (tzMinutes < 0) {
                            tzMinutes = -tzMinutes;
                            buffer.append('-');
                        } else {
                            buffer.append('+');
                        }
                        final int tzHour = tzMinutes / 60; // minutes to hours
                        tzMinutes -= tzHour * 60; // subtract hours from minutes in minutes
                        if (tzHour < 10) {
                            buffer.append(0);
                        }
                        buffer.append(tzHour).append(':');
                        if (tzMinutes < 10) {
                            buffer.append(0);
                        }
                        buffer.append(tzMinutes);
                    }
                    break;
                }
                // MMM dd hh:mm:ss (note dd should be (space)d if less than 10)
                case RFC3164: {
                    final DateFormatSymbols formatSymbols = DateFormatSymbols.getInstance(Locale.ENGLISH);
                    buffer.append(formatSymbols.getShortMonths()[month]).append(' ');
                    if (day < 10) {
                        buffer.append(' ');
                    }
                    buffer.append(day).append(' ');
                    if (hours < 10) {
                        buffer.append(0);
                    }
                    buffer.append(hours).append(':');
                    if (minutes < 10) {
                        buffer.append(0);
                    }
                    buffer.append(minutes).append(':');
                    if (seconds < 10) {
                        buffer.append(0);
                    }
                    buffer.append(seconds);
                    break;
                }
            }
        }
    }

    public static final InetAddress DEFAULT_ADDRESS;
    public static final int DEFAULT_PORT = 514;
    public static final Facility DEFAULT_FACILITY = Facility.USER_LEVEL;
    public static final char NILVALUE = '-';
    private static final String ENCODING = "utf-8";

    static {
        try {
            DEFAULT_ADDRESS = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Could not create address to localhost");
        }
    }

    private final Object outputLock = new Object();
    private InetAddress serverAddress;
    private int port;
    private String appName;
    private String hostname;
    private volatile Facility facility;
    private SyslogType syslogType;
    private final DatagramSocket datagramSocket;
    private final String pid;

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
     * @param hostname       the name of the host the messages are being sent from
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
     * @param hostname      the name of the host the messages are being sent from
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
     * @param hostname       the name of the host the messages are being sent from
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
     * @param hostname      the name of the host the messages are being sent from
     *
     * @throws IOException if an error occurs creating the UDP socket
     */
    public SyslogHandler(final InetAddress serverAddress, final int port, final Facility facility, final SyslogType syslogType, final String hostname) throws IOException {
        this.serverAddress = serverAddress;
        this.port = port;
        this.facility = facility;
        this.datagramSocket = new DatagramSocket();
        this.pid = findPid();
        this.appName = "java";
        this.hostname = hostname;
        this.syslogType = (syslogType == null ? SyslogType.RFC5424 : syslogType);
    }

    @Override
    public final void doPublish(final ExtLogRecord record) {
        // Don't log empty messages
        if (record.getMessage() == null || record.getMessage().isEmpty()) {
            return;
        }
        synchronized (outputLock) {
            if (datagramSocket.isClosed()) {
                throw new IllegalStateException("The syslog handler has been closed.");
            }
            try {
                final byte[] message = syslogType.format(record, facility, hostname, pid, appName);
                // TODO (jrp) allow TCP and other types
                final DatagramPacket dp = new DatagramPacket(message, message.length, serverAddress, port);
                datagramSocket.send(dp);
            } catch (IOException e) {
                reportError("Could not write to syslog", e, ErrorManager.WRITE_FAILURE);
            }
        }
        super.doPublish(record);
    }

    @Override
    public void close() {
        synchronized (outputLock) {
            safeClose(datagramSocket);
        }
        super.close();
    }

    @Override
    public void setFormatter(final Formatter newFormatter) throws SecurityException {
        super.setFormatter(newFormatter); // TODO maybe disable this method
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
        this.facility = facility;
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
     * Sets the host name which is used when sending the message to the syslog.
     * <p/>
     * This should be the name of the host sending the log messages, Note that the name cannot contain any whitespace.
     *
     * @param hostname the host name
     */
    public void setHostname(final String hostname) {
        if (hostname != null && hostname.contains(" ")) {
            throw new IllegalArgumentException(String.format("Host name '%s' is invalid. Whitespace is now allowed in the host name.", hostname));
        }
        this.hostname = hostname;
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

    static void safeClose(final DatagramSocket datagramSocket) {
        if (datagramSocket != null) try {
            if (!datagramSocket.isClosed()) {
                datagramSocket.close();
            }
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
                result = Integer.toString(Integer.valueOf(name.substring(0, index)));
            } catch (NumberFormatException ignore) {
                // ignore
            }
        }
        return result;
    }
}
