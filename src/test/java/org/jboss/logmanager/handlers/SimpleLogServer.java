/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class SimpleLogServer implements Runnable {


    protected final AtomicBoolean closed = new AtomicBoolean(false);
    protected final BlockingQueue<byte[]> receivedData = new LinkedBlockingQueue<byte[]>();

    static SimpleLogServer createUdp(final InetAddress address, final int port) throws IOException {
        return new SimpleLogServer.Udp(address, port);
    }

    static SimpleLogServer createTcp(final InetAddress address, final int port) throws IOException {
        final SimpleLogServer server = new SimpleLogServer.Tcp(address, port);
        final Thread thread = new Thread(server);
        thread.setDaemon(true);
        thread.start();
        return server;
    }

    static SimpleLogServer createTcp(final OutputStream out, final InetAddress address, final int port) throws IOException {
        final SimpleLogServer server = new SimpleLogServer.Tcp(out, address, port);
        final Thread thread = new Thread(server);
        thread.setDaemon(true);
        thread.start();
        return server;
    }

    abstract void close();

    byte[] receiveData() throws InterruptedException {
        return receivedData.poll(20, TimeUnit.SECONDS);
    }

    byte[] pollData() {
        return receivedData.poll();
    }

    static void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (Exception ignore) {

        }
    }

    private static class Udp extends SimpleLogServer {

        private final DatagramSocket socket;

        Udp(InetAddress address, int port) throws IOException {
            socket = new DatagramSocket(port);
        }

        @Override
        void close() {
            closed.set(true);
            socket.close();
        }

        @Override
        public void run() {

            while (!closed.get()) {
                try {
                    DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
                    socket.receive(packet);
                    byte[] bytes = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, bytes, 0, packet.getLength());
                    receivedData.add(bytes);
                } catch (IOException e) {
                    if (!closed.get()) {
                        e.printStackTrace();
                        close();
                    }
                }
            }
        }
    }

    private static class Tcp extends SimpleLogServer {

        private final ServerSocket serverSocket;
        private final OutputStream out;
        private volatile Socket socket;

        //Socket
        Tcp(InetAddress address, int port) throws IOException {
            serverSocket = new ServerSocket(port, 50, address);
            out = null;
        }

        //Socket
        Tcp(final OutputStream out, InetAddress address, int port) throws IOException {
            serverSocket = new ServerSocket(port, 50, address);
            this.out = out;
        }

        @Override
        void close() {
            closed.set(true);
            safeClose(serverSocket);
            safeClose(socket);
        }

        @Override
        public void run() {
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            try {
                InputStream in = socket.getInputStream();
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                while (!closed.get()) {
                    byte[] buf = new byte[512];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                        if (this.out != null) {
                            this.out.write(buf, 0, len);
                        }
                    }
                    if (out.toByteArray() != null && out.toByteArray().length > 0) {
                        receivedData.add(out.toByteArray());
                    }
                }
                if (out.toByteArray() != null && out.toByteArray().length > 0) {
                    receivedData.add(out.toByteArray());
                }
            } catch (IOException e) {
                if (!closed.get()) {
                    e.printStackTrace();
                    close();
                }
            }
        }
    }
}
