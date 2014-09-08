package org.jboss.logmanager.handlers;

import java.io.IOException;
import java.net.InetAddress;
import javax.net.ssl.SSLSocketFactory;

/**
 * An output stream that writes data to a {@link java.net.Socket socket}. Uses {@link
 * javax.net.ssl.SSLSocketFactory#getDefault()} to create the socket.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SslTcpOutputStream extends TcpOutputStream implements FlushableCloseable {

    /**
     * Creates a SSL TCP output stream.
     * <p/>
     * Uses the {@link javax.net.ssl.SSLSocketFactory#getDefault() default socket factory} to create the socket.
     *
     * @param address the address to connect to
     * @param port    the port to connect to
     *
     * @throws IOException if an I/O error occurs when creating the socket
     */
    public SslTcpOutputStream(final InetAddress address, final int port) throws IOException {
        super(SSLSocketFactory.getDefault(), address, port);
    }

    /**
     * Creates a SSL TCP output stream.
     * <p/>
     * Uses the {@link javax.net.ssl.SSLSocketFactory#getDefault() default socket factory} to create the socket.
     *
     * @param address          the address to connect to
     * @param port             the port to connect to
     * @param blockOnReconnect {@code true} to block when attempting to reconnect the socket or {@code false} to
     *                         reconnect asynchronously
     *
     * @throws IOException if an I/O error occurs when creating the socket
     */
    public SslTcpOutputStream(final InetAddress address, final int port, final boolean blockOnReconnect) throws IOException {
        super(SSLSocketFactory.getDefault(), address, port, blockOnReconnect);
    }
}
