package org.jboss.logmanager.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class UdpOutputStream extends OutputStream implements FlushableCloseable {
    private final DatagramSocket socket;

    public UdpOutputStream(final InetAddress address, final int port) throws IOException {
        socket = new DatagramSocket();
        socket.connect(address, port);
    }

    @Override
    public void write(final int b) throws IOException {
        final byte[] msg = new byte[] {(byte) b};
        final DatagramPacket packet = new DatagramPacket(msg, 1);
        socket.send(packet);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        if (b != null) {
            final DatagramPacket packet = new DatagramPacket(b, b.length);
            socket.send(packet);
        }
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (b != null) {
            final DatagramPacket packet = new DatagramPacket(b, off, len);
            socket.send(packet);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
