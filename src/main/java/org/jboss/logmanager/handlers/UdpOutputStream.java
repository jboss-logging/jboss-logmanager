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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * An output stream that writes data to a {@link java.net.DatagramSocket DatagramSocket}.
 *
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
