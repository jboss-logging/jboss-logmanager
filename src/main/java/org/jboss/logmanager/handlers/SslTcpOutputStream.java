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

    public SslTcpOutputStream(final InetAddress address, final int port) throws IOException {
        super(SSLSocketFactory.getDefault().createSocket(address, port));
    }
}
