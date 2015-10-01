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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;

/**
 * An output stream that writes data to a {@link java.net.Socket socket}.
 * <p/>
 * If an {@link java.io.IOException IOException} occurs during a {@link #write(byte[], int, int)} and a {@link
 * javax.net.SocketFactory socket factory} was defined the stream will attempt to reconnect indefinitely. By default
 * additional writes are discarded when reconnecting. If you set the {@link #setBlockOnReconnect(boolean) block on
 * reconnect} to {@code true}, then the reconnect will indefinitely block until the TCP stream is reconnected.
 * <p/>
 * You can optionally get a collection of the errors that occurred during a write or reconnect.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class TcpOutputStream extends OutputStream implements FlushableCloseable {
    private static final long retryTimeout = 5L;
    private static final long maxRetryTimeout = 40L;
    private static final int maxErrors = 10;

    protected final Object outputLock = new Object();

    private final SocketFactory socketFactory;
    private final InetAddress address;
    private final int port;
    private final Deque<Exception> errors = new ArrayDeque<Exception>(maxErrors);

    // Guarded by outputLock
    private Thread reconnectThread;
    // Guarded by outputLock
    private boolean blockOnReconnect;
    // Guarded by outputLock
    private Socket socket;
    // Guarded by outputLock
    private boolean connected;

    /**
     * Creates a TCP output stream.
     * <p/>
     * Uses the {@link javax.net.SocketFactory#getDefault() default socket factory} to create the socket.
     *
     * @param address the address to connect to
     * @param port    the port to connect to
     *
     * @throws IOException if an I/O error occurs when creating the socket
     */
    public TcpOutputStream(final InetAddress address, final int port) throws IOException {
        this(SocketFactory.getDefault(), address, port);
    }

    /**
     * Creates a TCP output stream.
     * <p>
     * Uses the {@link javax.net.SocketFactory#getDefault() default socket factory} to create the socket.
     * </p>
     * <p>
     * Note that if {@code blockOnReconnect} is set to {@code false} and the socket fails to connect in the constructor
     * reconnection won't be attempted. If set to {@code true} and the socket couldn't be connected during construction
     * a new reconnect thread will be launched in an attempt to reconnect.
     * </p>
     *
     * @param address          the address to connect to
     * @param port             the port to connect to
     * @param blockOnReconnect {@code true} to block when attempting to reconnect the socket or {@code false} to
     *                         reconnect asynchronously
     *
     * @throws IOException if an I/O error occurs when creating the socket
     */
    public TcpOutputStream(final InetAddress address, final int port, final boolean blockOnReconnect) throws IOException {
        this(SocketFactory.getDefault(), address, port, blockOnReconnect);
    }

    /**
     * Creates a new TCP output stream.
     * <p/>
     * <strong>Using this constructor does not allow for any kind of reconnect if the connection is dropped.</strong>
     *
     * @param socket the socket used to write the output to
     *
     * @deprecated Use {@link #TcpOutputStream(javax.net.SocketFactory, java.net.InetAddress, int)}
     */
    @Deprecated
    protected TcpOutputStream(final Socket socket) {
        this.socketFactory = null;
        this.address = null;
        this.port = -1;
        this.socket = socket;
        reconnectThread = null;
        connected = true;
    }

    /**
     * Creates a new TCP output stream.
     * <p/>
     * Creates a {@link java.net.Socket socket} from the {@code socketFactory} argument.
     *
     * @param socketFactory the factory used to create the socket
     * @param address       the address to connect to
     * @param port          the port to connect to
     *
     * @throws IOException if an I/O error occurs when creating the socket
     */
    protected TcpOutputStream(final SocketFactory socketFactory, final InetAddress address, final int port) throws IOException {
        this(socketFactory, address, port, false);
    }

    /**
     * Creates a new TCP output stream.
     * <p>
     * Creates a {@link java.net.Socket socket} from the {@code socketFactory} argument.
     * </p>
     * <p>
     * Note that if {@code blockOnReconnect} is set to {@code false} and the socket fails to connect in the constructor
     * reconnection won't be attempted. If set to {@code true} and the socket couldn't be connected during construction
     * a new reconnect thread will be launched in an attempt to reconnect.
     * </p>
     *
     * @param socketFactory    the factory used to create the socket
     * @param address          the address to connect to
     * @param port             the port to connect to
     * @param blockOnReconnect {@code true} to block when attempting to reconnect the socket or {@code false} to
     *                         reconnect asynchronously
     *
     * @throws IOException if an I/O error occurs when creating the socket
     */
    protected TcpOutputStream(final SocketFactory socketFactory, final InetAddress address, final int port, final boolean blockOnReconnect) throws IOException {
        this.socketFactory = socketFactory;
        this.address = address;
        this.port = port;
        this.blockOnReconnect = blockOnReconnect;
        if (blockOnReconnect) {
            socket = socketFactory.createSocket(address, port);
        } else {
            try {
                socket = socketFactory.createSocket(address, port);
            } catch (IOException e) {
                reconnectThread = createThread();
                reconnectThread.start();
            }
        }
        connected = true;
    }

    @Override
    public void write(final int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        synchronized (outputLock) {
            try {
                if (connected) {
                    socket.getOutputStream().write(b, off, len);
                }
            } catch (SocketException e) {
                if (socketFactory != null && reconnectThread == null) {
                    reconnectThread = createThread();
                    addError(e);
                    connected = false;
                    // Close the previous socket
                    try {
                        socket.close();
                    } catch (IOException ignore) {
                    }
                    if (blockOnReconnect) {
                        reconnectThread.run();
                        // We should be reconnected, try to write again
                        write(b, off, len);
                    } else {
                        reconnectThread.start();
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    @Override
    public void flush() throws IOException {
        synchronized (outputLock) {
            try {
                socket.getOutputStream().flush();
            } catch (SocketException e) {
                // This should likely never be hit, but should attempt to reconnect if it does happen
                if (socketFactory != null && reconnectThread == null) {
                    reconnectThread = createThread();
                    addError(e);
                    connected = false;
                    // Close the previous socket
                    try {
                        socket.close();
                    } catch (IOException ignore) {
                    }
                    if (blockOnReconnect) {
                        reconnectThread.run();
                    } else {
                        reconnectThread.start();
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (outputLock) {
            socket.close();
        }
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
     * Enables or disables blocking when attempting to reconnect the socket.
     * <p/>
     * If set to {@code true} the {@code write} methods will block when attempting to reconnect. This is only advisable
     * to be set to {@code true} if using an asynchronous handler.
     *
     * @param blockOnReconnect {@code true} to block when reconnecting or {@code false} to reconnect asynchronously
     *                         discarding any new messages coming in
     */
    public void setBlockOnReconnect(final boolean blockOnReconnect) {
        synchronized (outputLock) {
            this.blockOnReconnect = blockOnReconnect;
        }
    }

    /**
     * Returns the connected state of the TCP stream.
     * <p/>
     * The stream is said to be disconnected when an {@link java.io.IOException} occurs during a write. Otherwise a
     * stream is considered connected.
     *
     * @return {@code true} if the stream is connected, otherwise {@code false}
     */
    public boolean isConnected() {
        synchronized (outputLock) {
            return connected;
        }
    }

    /**
     * Retrieves the errors occurred, if any, during a write or reconnect.
     *
     * @return a collection of errors or an empty list
     */
    public Collection<Exception> getErrors() {
        synchronized (errors) {
            if (!errors.isEmpty()) {
                // drain the errors and return a list
                final List<Exception> result = new ArrayList<Exception>(errors);
                errors.clear();
                return result;
            }
        }
        return Collections.emptyList();
    }

    private void addError(final Exception e) {
        synchronized (errors) {
            if (errors.size() < maxErrors) {
                errors.addLast(e);
            }
            // TODO (jrp) should we do something with these errors
        }
    }

    private Thread createThread() {
        final Thread thread = new Thread(new RetryConnector());
        thread.setDaemon(true);
        thread.setName("LogManager Socket Reconnect Thread");
        return thread;
    }

    private class RetryConnector implements Runnable {
        private int attempts = 0;

        @Override
        public void run() {
            if (socketFactory != null) {
                try {
                    final Socket socket = socketFactory.createSocket(address, port);
                    synchronized (outputLock) {
                        TcpOutputStream.this.socket = socket;
                        connected = true;
                        reconnectThread = null;
                    }
                } catch (IOException e) {
                    addError(e);
                    final long timeout;
                    if (attempts++ > 0L) {
                        timeout = (10 * attempts);
                    } else {
                        timeout = retryTimeout;
                    }
                    // Wait for a bit, then try to reconnect
                    try {
                        TimeUnit.SECONDS.sleep(Math.min(timeout, maxRetryTimeout));
                    } catch (InterruptedException ignore) {
                    }
                    run();
                }
            }
        }
    }
}
