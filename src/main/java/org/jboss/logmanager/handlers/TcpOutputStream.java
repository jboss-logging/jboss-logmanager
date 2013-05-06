package org.jboss.logmanager.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.SocketFactory;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class TcpOutputStream extends OutputStream implements FlushableCloseable {
    private final Socket socket;

    public TcpOutputStream(final InetAddress address, final int port) throws IOException {
        this(SocketFactory.getDefault().createSocket(address, port));
    }

    TcpOutputStream(final Socket socket) {
        this.socket = socket;
    }

    @Override
    public void write(final int b) throws IOException {
        socket.getOutputStream().write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        socket.getOutputStream().write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        socket.getOutputStream().write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        socket.getOutputStream().flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
