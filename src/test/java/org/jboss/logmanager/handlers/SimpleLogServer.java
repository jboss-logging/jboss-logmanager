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
