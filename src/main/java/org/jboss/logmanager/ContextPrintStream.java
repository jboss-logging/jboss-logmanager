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

package org.jboss.logmanager;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.Locale;

public final class ContextPrintStream extends PrintStream {

    private static final class StreamHolder extends InheritableThreadLocal<PrintStream> {
        private final PrintStream initialValue;

        private StreamHolder(final PrintStream initialValue) {
            this.initialValue = initialValue;
        }

        protected PrintStream initialValue() {
            return initialValue;
        }
    }

    private final ThreadLocal<PrintStream> delegateHolder;

    public ContextPrintStream(PrintStream defaultDelegate) {
        super((OutputStream)null);
        delegateHolder = new StreamHolder(defaultDelegate);
    }

    public PrintStream swapDelegate(final PrintStream newDelegate) {
        try {
            return delegateHolder.get();
        } finally {
            delegateHolder.set(newDelegate);
        }
    }

    public static PrintStream getAndSetContextSystemOut(final PrintStream newSystemOut) {
        return ((ContextPrintStream)System.out).swapDelegate(newSystemOut);
    }

    public static PrintStream getAndSetContextSystemErr(final PrintStream newSystemOut) {
        return ((ContextPrintStream)System.err).swapDelegate(newSystemOut);
    }

    public static void initSystemStreams(final PrintStream defaultSystemOut, final PrintStream defaultSystemErr) {
        System.setOut(new ContextPrintStream(defaultSystemOut));
        System.setErr(new ContextPrintStream(defaultSystemErr));
    }

    public void close() {
    }

    public void flush() {
        delegateHolder.get().flush();
    }

    public boolean checkError() {
        return false;
    }

    public void write(final int b) {
        delegateHolder.get().write(b);
    }

    public void write(final byte[] buf, final int off, final int len) {
        delegateHolder.get().write(buf, off, len);
    }

    public void print(final boolean b) {
        delegateHolder.get().print(b);
    }

    public void print(final char c) {
        delegateHolder.get().print(c);
    }

    public void print(final int i) {
        delegateHolder.get().print(i);
    }

    public void print(final long l) {
        delegateHolder.get().print(l);
    }

    public void print(final float f) {
        delegateHolder.get().print(f);
    }

    public void print(final double d) {
        delegateHolder.get().print(d);
    }

    public void print(final char[] s) {
        delegateHolder.get().print(s);
    }

    public void print(final String s) {
        delegateHolder.get().print(s);
    }

    public void print(final Object obj) {
        delegateHolder.get().print(obj);
    }

    public void println() {
        delegateHolder.get().println();
    }

    public void println(final boolean x) {
        delegateHolder.get().println(x);
    }

    public void println(final char x) {
        delegateHolder.get().println(x);
    }

    public void println(final int x) {
        delegateHolder.get().println(x);
    }

    public void println(final long x) {
        delegateHolder.get().println(x);
    }

    public void println(final float x) {
        delegateHolder.get().println(x);
    }

    public void println(final double x) {
        delegateHolder.get().println(x);
    }

    public void println(final char[] x) {
        delegateHolder.get().println(x);
    }

    public void println(final String x) {
        delegateHolder.get().println(x);
    }

    public void println(final Object x) {
        delegateHolder.get().println(x);
    }

    public PrintStream printf(final String format, final Object... args) {
        return delegateHolder.get().printf(format, args);
    }

    public PrintStream printf(final Locale l, final String format, final Object... args) {
        return delegateHolder.get().printf(l, format, args);
    }

    public PrintStream format(final String format, final Object... args) {
        return delegateHolder.get().format(format, args);
    }

    public PrintStream format(final Locale l, final String format, final Object... args) {
        return delegateHolder.get().format(l, format, args);
    }

    public PrintStream append(final CharSequence csq) {
        return delegateHolder.get().append(csq);
    }

    public PrintStream append(final CharSequence csq, final int start, final int end) {
        return delegateHolder.get().append(csq, start, end);
    }

    public PrintStream append(final char c) {
        return delegateHolder.get().append(c);
    }

    public void write(final byte[] b) throws IOException {
        delegateHolder.get().write(b);
    }

    public void setError() {
    }

    public void clearError() {
    }
}
