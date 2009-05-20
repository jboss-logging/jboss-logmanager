/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.stdio;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.security.Permission;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A context for console input and output.
 */
public final class StdioContext {
    private static final StdioContext SYSTEM_STDIO_CONTEXT = new StdioContext(System.in, System.out, System.err);

    private static final Permission CREATE_CONTEXT_PERMISSION = new RuntimePermission("createStdioContext", null);
    private static final Permission SET_CONTEXT_SELECTOR_PERMISSION = new RuntimePermission("setStdioContextSelector", null);
    private static final Permission INSTALL_PERMISSION = new RuntimePermission("installStdioContextSelector", null);

    private enum State {
        UNINSTALLED,
        INSTALLING,
        INSTALLED,
        UNINSTALLING,
    }

    private static final AtomicReference<State> state = new AtomicReference<State>(State.UNINSTALLED);

    private final InputStream in;
    private final PrintStream out;
    private final PrintStream err;

    StdioContext(final InputStream in, final PrintStream out, final PrintStream err) {
        this.in = in;
        this.out = out;
        this.err = err;
    }

    /**
     * Create a console I/O context.
     *
     * @param in the input stream for this context
     * @param out the output stream for this context
     * @param err the error stream for this context
     * @return the new context
     * @throws SecurityException if the caller does not have the {@code createStdioContext} {@link RuntimePermission}
     */
    public static StdioContext create(final InputStream in, final PrintStream out, final PrintStream err) throws SecurityException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_CONTEXT_PERMISSION);
        }
        return new StdioContext(in, out, err);
    }

    /**
     * Get the current console I/O context.
     *
     * @return the current context
     */
    public static StdioContext getStdioContext() {
        return stdioContextSelector.getStdioContext();
    }

    /**
     * Get the input stream for this context.
     *
     * @return the input stream
     */
    public InputStream getIn() {
        return in;
    }

    /**
     * Get the output stream for this context.
     *
     * @return the output stream
     */
    public PrintStream getOut() {
        return out;
    }

    /**
     * Get the error stream for this context.
     *
     * @return the error stream
     */
    public PrintStream getErr() {
        return err;
    }

    private static volatile StdioContextSelector stdioContextSelector = new StdioContextSelector() {
        public StdioContext getStdioContext() {
            return SYSTEM_STDIO_CONTEXT;
        }
    };

    /**
     * Install the StdioContext streams.
     *
     * @throws SecurityException if the caller does not have the {@code installStdioContextSelector} {@link RuntimePermission}
     * @throws IllegalStateException if the streams are already installed
     */
    public static void install() throws SecurityException, IllegalStateException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(INSTALL_PERMISSION);
        }
        if (! state.compareAndSet(State.UNINSTALLED, State.INSTALLING)) {
            throw new IllegalStateException("Already installed");
        }
        System.setOut(new DelegatingPrintStream() {
            PrintStream getDelegate() {
                return stdioContextSelector.getStdioContext().out;
            }
        });
        System.setErr(new DelegatingPrintStream() {
            PrintStream getDelegate() {
                return stdioContextSelector.getStdioContext().err;
            }
        });
        System.setIn(new DelegatingInputStream() {
            InputStream getDelegate() {
                return stdioContextSelector.getStdioContext().in;
            }
        });
        state.set(State.INSTALLED);
    }

    /**
     * Uninstall the StdioContext streams.
     *
     * @throws SecurityException if the caller does not have the {@code installStdioContextSelector} {@link RuntimePermission}
     * @throws IllegalStateException if the streams are already uninstalled
     */
    public static void uninstall() throws SecurityException, IllegalStateException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(INSTALL_PERMISSION);
        }
        if (! state.compareAndSet(State.INSTALLED, State.UNINSTALLING)) {
            throw new IllegalStateException("Already uninstalled");
        }
        System.setOut(SYSTEM_STDIO_CONTEXT.out);
        System.setErr(SYSTEM_STDIO_CONTEXT.err);
        System.setIn(SYSTEM_STDIO_CONTEXT.in);
        state.set(State.UNINSTALLED);
    }

    public static void setStdioContextSelector(final StdioContextSelector stdioContextSelector) {
        if (stdioContextSelector == null) {
            throw new NullPointerException("stdioContextSelector is null");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_CONTEXT_SELECTOR_PERMISSION);
        }
        StdioContext.stdioContextSelector = stdioContextSelector;
    }

    private static abstract class DelegatingPrintStream extends PrintStream {

        protected DelegatingPrintStream() {
            super((OutputStream) null);
        }

        abstract PrintStream getDelegate();

        public void flush() {
            getDelegate().flush();
        }

        public void close() {
            getDelegate().close();
        }

        public boolean checkError() {
            return getDelegate().checkError();
        }

        public void write(final int b) {
            getDelegate().write(b);
        }

        public void write(final byte[] buf, final int off, final int len) {
            getDelegate().write(buf, off, len);
        }

        public void print(final boolean b) {
            getDelegate().print(b);
        }

        public void print(final char c) {
            getDelegate().print(c);
        }

        public void print(final int i) {
            getDelegate().print(i);
        }

        public void print(final long l) {
            getDelegate().print(l);
        }

        public void print(final float f) {
            getDelegate().print(f);
        }

        public void print(final double d) {
            getDelegate().print(d);
        }

        public void print(final char[] s) {
            getDelegate().print(s);
        }

        public void print(final String s) {
            getDelegate().print(s);
        }

        public void print(final Object obj) {
            getDelegate().print(obj);
        }

        public void println() {
            getDelegate().println();
        }

        public void println(final boolean x) {
            getDelegate().println(x);
        }

        public void println(final char x) {
            getDelegate().println(x);
        }

        public void println(final int x) {
            getDelegate().println(x);
        }

        public void println(final long x) {
            getDelegate().println(x);
        }

        public void println(final float x) {
            getDelegate().println(x);
        }

        public void println(final double x) {
            getDelegate().println(x);
        }

        public void println(final char[] x) {
            getDelegate().println(x);
        }

        public void println(final String x) {
            getDelegate().println(x);
        }

        public void println(final Object x) {
            getDelegate().println(x);
        }

        public PrintStream printf(final String format, final Object... args) {
            return getDelegate().printf(format, args);
        }

        public PrintStream printf(final Locale l, final String format, final Object... args) {
            return getDelegate().printf(l, format, args);
        }

        public PrintStream format(final String format, final Object... args) {
            return getDelegate().format(format, args);
        }

        public PrintStream format(final Locale l, final String format, final Object... args) {
            return getDelegate().format(l, format, args);
        }

        public PrintStream append(final CharSequence csq) {
            return getDelegate().append(csq);
        }

        public PrintStream append(final CharSequence csq, final int start, final int end) {
            return getDelegate().append(csq, start, end);
        }

        public PrintStream append(final char c) {
            return getDelegate().append(c);
        }
    }

    private static abstract class DelegatingInputStream extends InputStream {
        abstract InputStream getDelegate();

        public int read() throws IOException {
            return getDelegate().read();
        }

        public int read(final byte[] b) throws IOException {
            return getDelegate().read(b);
        }

        public int read(final byte[] b, final int off, final int len) throws IOException {
            return getDelegate().read(b, off, len);
        }

        public long skip(final long n) throws IOException {
            return getDelegate().skip(n);
        }

        public int available() throws IOException {
            return getDelegate().available();
        }

        public void close() throws IOException {
            getDelegate().close();
        }

        public void mark(final int readlimit) {
            getDelegate().mark(readlimit);
        }

        public void reset() throws IOException {
            getDelegate().reset();
        }

        public boolean markSupported() {
            return getDelegate().markSupported();
        }
    }
}
