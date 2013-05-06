package org.jboss.logmanager.handlers;

import java.io.IOException;
import java.net.InetAddress;
import javax.net.ssl.SSLSocketFactory;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SslTcpOutputStream extends TcpOutputStream implements FlushableCloseable {

    SslTcpOutputStream(final InetAddress address, final int port) throws IOException {
        super(SSLSocketFactory.getDefault().createSocket(address, port));
    }
}
