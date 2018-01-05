/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.jboss.logmanager.ExtLogRecord;

/**
 * A handler used to communicate over a socket.
 * <p>
 * By default this handler will {@linkplain #isAutoActivate() automatically activate} itself. If messages need to be
 * queued until the handler is appropriately configured the {@linkplain #setAutoActivate(boolean) automatic activation}
 * should be set to {@code false}.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class SocketHandler extends DelayedHandler {

    /**
     * The type of socket
     */
    public enum Protocol {
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

    public static final int DEFAULT_PORT = 4560;

    // All the following fields are guarded by outputLock
    private SocketFactory socketFactory;
    private InetAddress address;
    private int port;
    private Protocol protocol;
    private boolean blockOnReconnect;
    private Writer writer;
    private boolean initialize;
    private boolean autoActivate;

    /**
     * Creates a socket handler with an address of {@linkplain java.net.InetAddress#getLocalHost() localhost} and port
     * of {@linkplain #DEFAULT_PORT 4560}.
     *
     * @throws UnknownHostException if an error occurs attempting to retrieve the localhost
     */
    public SocketHandler() throws UnknownHostException {
        this(InetAddress.getLocalHost(), DEFAULT_PORT);
    }

    /**
     * Creates a socket handler.
     *
     * @param hostname the hostname to connect to
     * @param port     the port to connect to
     *
     * @throws UnknownHostException if an error occurs resolving the address
     */
    public SocketHandler(final String hostname, final int port) throws UnknownHostException {
        this(InetAddress.getByName(hostname), port);
    }

    /**
     * Creates a socket handler.
     *
     * @param address the address to connect to
     * @param port    the port to connect to
     */
    public SocketHandler(final InetAddress address, final int port) {
        this(Protocol.TCP, address, port);
    }

    /**
     * Creates a socket handler.
     *
     * @param protocol the protocol to connect with
     * @param hostname the hostname to connect to
     * @param port     the port to connect to
     *
     * @throws UnknownHostException if an error occurs resolving the hostname
     */
    public SocketHandler(final Protocol protocol, final String hostname, final int port) throws UnknownHostException {
        this(protocol, InetAddress.getByName(hostname), port);
    }

    /**
     * Creates a socket handler.
     *
     * @param protocol the protocol to connect with
     * @param address  the address to connect to
     * @param port     the port to connect to
     */
    public SocketHandler(final Protocol protocol, final InetAddress address, final int port) {
        this(null, protocol, address, port);
    }

    /**
     * Creates a socket handler.
     *
     * @param socketFactory the socket factory to use for creating {@linkplain Protocol#TCP TCP} or
     *                      {@linkplain Protocol#SSL_TCP SSL TCP} connections, if {@code null} a default factory will
     *                      be used
     * @param protocol      the protocol to connect with
     * @param hostname      the hostname to connect to
     * @param port          the port to connect to
     *
     * @throws UnknownHostException if an error occurs resolving the hostname
     */
    public SocketHandler(final SocketFactory socketFactory, final Protocol protocol, final String hostname, final int port) throws UnknownHostException {
        this(socketFactory, protocol, InetAddress.getByName(hostname), port);
    }

    /**
     * Creates a socket handler.
     *
     * @param socketFactory the socket factory to use for creating {@linkplain Protocol#TCP TCP} or
     *                      {@linkplain Protocol#SSL_TCP SSL TCP} connections, if {@code null} a default factory will
     *                      be used
     * @param protocol      the protocol to connect with
     * @param address       the address to connect to
     * @param port          the port to connect to
     */
    public SocketHandler(final SocketFactory socketFactory, final Protocol protocol, final InetAddress address, final int port) {
        this.address = address;
        this.port = port;
        this.protocol = protocol;
        initialize = true;
        writer = null;
        this.socketFactory = socketFactory;
        blockOnReconnect = false;
        autoActivate = true;
    }

    @Override
    protected void doPublish(final ExtLogRecord record) {
        final String formatted;
        final Formatter formatter = getFormatter();
        try {
            formatted = formatter.format(record);
        } catch (Exception e) {
            reportError("Could not format message", e, ErrorManager.FORMAT_FAILURE);
            return;
        }
        if (formatted.isEmpty()) {
            // nothing to write; move along
            return;
        }
        try {
            synchronized (outputLock) {
                if (initialize) {
                    initialize();
                    initialize = false;
                }
                if (writer == null) {
                    return;
                }
                writer.write(formatted);
                super.doPublish(record);
            }
        } catch (Exception e) {
            reportError("Error writing log message", e, ErrorManager.WRITE_FAILURE);
        }
    }

    @Override
    public void flush() {
        synchronized (outputLock) {
            safeFlush(writer);
        }
        super.flush();
    }

    @Override
    protected void closeResources() {
        synchronized (outputLock) {
            safeClose(writer);
            writer = null;
            initialize = true;
        }
    }

    @Override
    protected boolean requiresActivation() {
        synchronized (outputLock) {
            return initialize;
        }
    }

    /**
     * Returns the address being used.
     *
     * @return the address
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Sets the address to connect to.
     *
     * @param address the address
     */
    public void setAddress(final InetAddress address) {
        checkAccess(this);
        synchronized (outputLock) {
            this.address = address;
            initialize = true;
        }
    }

    @Override
    public boolean isAutoActivate() {
        synchronized (outputLock) {
            return autoActivate;
        }
    }

    /**
     * Sets whether or not the handler should be {@linkplain #activate() activated} when the first record is written.
     * If set to {@code false} {@link #activate()} is required to be invoked explicitly.
     *
     * @param autoActivate {@code true} if {@link #activate()} should be automatically invoked, otherwise
     *                     {@code false} if {@link #activate()} should be explicitly invoked
     */
    public void setAutoActivate(final boolean autoActivate) {
        checkAccess(this);
        synchronized (outputLock) {
            this.autoActivate = autoActivate;
        }
    }

    /**
     * Sets the address to connect to by doing a lookup on the hostname.
     *
     * @param hostname the host name used to resolve the address
     *
     * @throws UnknownHostException if an error occurs resolving the address
     */
    public void setHostname(final String hostname) throws UnknownHostException {
        checkAccess(this);
        setAddress(InetAddress.getByName(hostname));
    }

    /**
     * Indicates whether or not the output stream is set to block when attempting to reconnect a TCP connection.
     *
     * @return {@code true} if blocking is enabled, otherwise {@code false}
     */
    public boolean isBlockOnReconnect() {
        synchronized (outputLock) {
            return blockOnReconnect;
        }
    }

    /**
     * Enables or disables blocking when attempting to reconnect the socket when using a {@linkplain Protocol#TCP TCP}
     * or {@linkplain Protocol#SSL_TCP SSL TCP} connections.
     * <p/>
     * If set to {@code true} the {@code write} methods will block when attempting to reconnect. This is only advisable
     * to be set to {@code true} if using an asynchronous handler.
     *
     * @param blockOnReconnect {@code true} to block when reconnecting or {@code false} to reconnect asynchronously
     *                         discarding any new messages coming in
     */
    public void setBlockOnReconnect(final boolean blockOnReconnect) {
        checkAccess(this);
        synchronized (outputLock) {
            this.blockOnReconnect = blockOnReconnect;
        }
    }

    /**
     * Returns the protocol being used.
     *
     * @return the protocol
     */
    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * Sets the protocol to use. If the value is {@code null} the protocol will be set to
     * {@linkplain Protocol#TCP TCP}.
     * <p>
     * Note that is resets the {@linkplain #setSocketFactory(SocketFactory) socket factory}.
     * </p>
     *
     * @param protocol the protocol to use
     */
    public void setProtocol(final Protocol protocol) {
        checkAccess(this);
        synchronized (outputLock) {
            if (protocol == null) {
                this.protocol = Protocol.TCP;
            }
            // Reset the socket factory
            socketFactory = null;
            this.protocol = protocol;
            initialize = true;
        }
    }

    /**
     * Returns the port being used.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the port to connect to.
     *
     * @param port the port
     */
    public void setPort(final int port) {
        checkAccess(this);
        synchronized (outputLock) {
            this.port = port;
            initialize = true;
        }
    }

    /**
     * Sets the socket factory to use for creating {@linkplain Protocol#TCP TCP} or {@linkplain Protocol#SSL_TCP SSL}
     * connections.
     * <p>
     * Note that if the {@linkplain #setProtocol(Protocol) protocol} is set the socket factory will be set to
     * {@code null} and reset.
     * </p>
     *
     * @param socketFactory the socket factory
     */
    public void setSocketFactory(final SocketFactory socketFactory) {
        checkAccess(this);
        synchronized (outputLock) {
            this.socketFactory = socketFactory;
            initialize = true;
        }
    }

    private void initialize() {
        final Writer current = this.writer;
        boolean okay = false;
        try {
            if (current != null) {
                writeTail(current);
                safeFlush(current);
            }
            // Close the current writer before we attempt to create a new connection
            safeClose(current);
            final OutputStream out = createOutputStream();
            if (out == null) {
                return;
            }
            final String encoding = getEncoding();
            final UninterruptibleOutputStream outputStream = new UninterruptibleOutputStream(out);
            if (encoding == null) {
                writer = new OutputStreamWriter(outputStream);
            } else {
                writer = new OutputStreamWriter(outputStream, encoding);
            }
            writeHead(writer);
            okay = true;
        } catch (UnsupportedEncodingException e) {
            reportError("Error opening", e, ErrorManager.OPEN_FAILURE);
        } finally {
            safeClose(current);
            if (!okay) {
                safeClose(writer);
            }
        }

    }

    private OutputStream createOutputStream() {
        if (address != null || port >= 0) {
            try {
                if (protocol == Protocol.UDP) {
                    return new UdpOutputStream(address, port);
                }
                SocketFactory socketFactory = this.socketFactory;
                if (socketFactory == null) {
                    if (protocol == Protocol.SSL_TCP) {
                        this.socketFactory = socketFactory = SSLSocketFactory.getDefault();
                    } else {
                        // Assume we want a TCP connection
                        this.socketFactory = socketFactory = SocketFactory.getDefault();
                    }
                }
                return new TcpOutputStream(socketFactory, address, port, blockOnReconnect);
            } catch (IOException e) {
                reportError("Failed to create socket output stream", e, ErrorManager.OPEN_FAILURE);
            }
        }
        return null;
    }

    private void writeHead(final Writer writer) {
        try {
            final Formatter formatter = getFormatter();
            if (formatter != null) writer.write(formatter.getHead(this));
        } catch (Exception e) {
            reportError("Error writing section header", e, ErrorManager.WRITE_FAILURE);
        }
    }

    private void writeTail(final Writer writer) {
        try {
            final Formatter formatter = getFormatter();
            if (formatter != null) writer.write(formatter.getTail(this));
        } catch (Exception ex) {
            reportError("Error writing section tail", ex, ErrorManager.WRITE_FAILURE);
        }
    }

    private void safeClose(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (Exception e) {
            reportError("Error closing resource", e, ErrorManager.CLOSE_FAILURE);
        } catch (Throwable ignored) {
        }
    }

    private void safeFlush(Flushable f) {
        try {
            if (f != null) f.flush();
        } catch (Exception e) {
            reportError("Error on flush", e, ErrorManager.FLUSH_FAILURE);
        } catch (Throwable ignored) {
        }
    }
}
