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

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Base64;
import java.util.Objects;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;

import org.jboss.logmanager.errormanager.HandlerErrorManager;
import org.jboss.logmanager.formatters.Formatters;

/**
 * A console handler which writes to {@code System.out} by default.
 */
public class ConsoleHandler extends OutputStreamHandler {
    private static final OutputStream out = System.out;
    private static final OutputStream err = System.err;

    /**
     * The target stream type.
     */
    public enum Target {

        /**
         * The target for {@link System#out}.
         */
        SYSTEM_OUT,
        /**
         * The target for {@link System#err}.
         */
        SYSTEM_ERR,
        /**
         * The target for {@link System#console()}.
         */
        CONSOLE,
    }

    private final ErrorManager localErrorManager = new HandlerErrorManager(this);

    /**
     * Construct a new instance.
     */
    public ConsoleHandler() {
        this(Formatters.nullFormatter());
    }

    /**
     * Construct a new instance.
     *
     * @param formatter the formatter to use
     */
    public ConsoleHandler(final Formatter formatter) {
        this(defaultTarget(), formatter);
    }

    /**
     * Construct a new instance.
     *
     * @param target the target to write to, or {@code null} to start with an uninitialized target
     */
    public ConsoleHandler(final Target target) {
        this(target, Formatters.nullFormatter());
    }

    /**
     * Construct a new instance.
     *
     * @param target    the target to write to, or {@code null} to start with an uninitialized target
     * @param formatter the formatter to use
     */
    public ConsoleHandler(final Target target, final Formatter formatter) {
        super(formatter);
        setCharset(JDKSpecific.consoleCharset());
        switch (target) {
            case SYSTEM_OUT:
                setOutputStream(wrap(out));
                break;
            case SYSTEM_ERR:
                setOutputStream(wrap(err));
                break;
            case CONSOLE:
                setWriter(wrap(ConsoleHolder.console));
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * Set the target for this console handler.
     *
     * @param target the target to write to, or {@code null} to clear the target
     */
    public void setTarget(Target target) {
        final Target t = (target == null ? defaultTarget() : target);
        switch (t) {
            case SYSTEM_OUT:
                setOutputStream(wrap(out));
                break;
            case SYSTEM_ERR:
                setOutputStream(wrap(err));
                break;
            case CONSOLE:
                setWriter(wrap(ConsoleHolder.console));
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    public void setErrorManager(final ErrorManager em) {
        if (em == localErrorManager) {
            // ignore to avoid loops
            super.setErrorManager(new ErrorManager());
            return;
        }
        super.setErrorManager(em);
    }

    private static final String ESC = Character.toString(27);

    /**
     * Write a PNG image to the console log, if it is supported.
     * The image data stream must be closed by the caller.
     *
     * @param imageData the PNG image data stream to write (must not be {@code null})
     * @param columns   the number of text columns to occupy (0 for automatic)
     * @param rows      the number of text rows to occupy (0 for automatic)
     * @return {@code true} if the image was written, or {@code false} if image support isn't found
     * @throws IOException if the stream failed while writing the image
     */
    public boolean writeImagePng(InputStream imageData, int columns, int rows) throws IOException {
        Objects.requireNonNull(imageData, "imageData");
        columns = Math.max(0, columns);
        rows = Math.max(0, rows);
        if (!isGraphicsSupportPassivelyDetected()) {
            // no graphics
            return false;
        }
        lock.lock();
        try {
            // clear out any pending stuff
            final Writer writer = getWriter();
            if (writer == null)
                return false;
            // start with the header
            try (OutputStream os = Base64.getEncoder().wrap(new OutputStream() {
                final byte[] buffer = new byte[2048];
                int pos = 0;

                public void write(final int b) throws IOException {
                    if (pos == buffer.length)
                        more();
                    buffer[pos++] = (byte) b;
                }

                public void write(final byte[] b, int off, int len) throws IOException {
                    while (len > 0) {
                        if (pos == buffer.length) {
                            more();
                        }
                        final int cnt = Math.min(len, buffer.length - pos);
                        System.arraycopy(b, off, buffer, pos, cnt);
                        pos += cnt;
                        off += cnt;
                        len -= cnt;
                    }
                }

                void more() throws IOException {
                    writer.write("m=1;");
                    writer.write(new String(buffer, 0, pos, StandardCharsets.US_ASCII));
                    writer.write(ESC + "\\");
                    // set up next segment
                    writer.write(ESC + "_G");
                    pos = 0;
                }

                public void close() throws IOException {
                    writer.write("m=0;");
                    writer.write(new String(buffer, 0, pos, StandardCharsets.US_ASCII));
                    writer.write(ESC + "\\\n");
                    writer.flush();
                    pos = 0;
                }
            })) {
                // set the header
                writer.write(String.format(ESC + "_Gf=100,a=T,c=%d,r=%d,", Integer.valueOf(columns), Integer.valueOf(rows)));
                // write the data in encoded chunks
                imageData.transferTo(os);
            }
            // OK
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the local error manager. This is an error manager that will publish errors to this console handler.
     * The console handler itself should not use this error manager.
     *
     * @return the local error manager
     */
    public ErrorManager getLocalErrorManager() {
        return localErrorManager;
    }

    private static OutputStream wrap(final OutputStream outputStream) {
        return outputStream == null ? null
                : outputStream instanceof UncloseableOutputStream ? outputStream : new UncloseableOutputStream(outputStream);
    }

    private static Writer wrap(final Writer writer) {
        return writer == null ? null : writer instanceof UncloseableWriter ? writer : new UncloseableWriter(writer);
    }

    /** {@inheritDoc} */
    public void setOutputStream(final OutputStream outputStream) {
        super.setOutputStream(wrap(outputStream));
    }

    /**
     * Determine whether the console exists.
     * If the console does not exist, then the standard output stream will be used when {@link Target#CONSOLE} is
     * selected as {@linkplain #setTarget(Target) the output target}.
     *
     * @return {@code true} if there is a console, {@code false} otherwise
     */
    public static boolean hasConsole() {
        return ConsoleHolder.console != null;
    }

    /**
     * Determine whether the console supports truecolor output.
     * This call may be expensive, so the result should be captured for the lifetime of any formatter making use of
     * this information.
     *
     * @return {@code true} if the console exists and supports truecolor output; {@code false} otherwise
     */
    public static boolean isTrueColor() {
        if (!hasConsole()) {
            return false;
        }
        final String colorterm = System.getenv("COLORTERM");
        return colorterm != null && (colorterm.contains("truecolor") || colorterm.contains("24bit"));
    }

    /**
     * Determine whether the console can be passively detected to support graphical output.
     * This call may be expensive, so the result should be captured for the lifetime of any formatter making use of
     * this information.
     *
     * @return {@code true} if the console exists and supports graphical output; {@code false} otherwise or if
     *         graphical support cannot be passively detected
     */
    public static boolean isGraphicsSupportPassivelyDetected() {
        if (!hasConsole()) {
            return false;
        }
        final String term = System.getenv("TERM");
        final String termProgram = System.getenv("TERM_PROGRAM");
        return term != null && (term.equalsIgnoreCase("kitty")
                || term.equalsIgnoreCase("xterm-kitty")
                || term.equalsIgnoreCase("wezterm")
                || term.equalsIgnoreCase("konsole")) || termProgram != null && termProgram.equalsIgnoreCase("wezterm");
    }

    private static Target defaultTarget() {
        return ConsoleHolder.console == null ? Target.SYSTEM_OUT : Target.CONSOLE;
    }

    private static final class ConsoleHolder {
        private static final PrintWriter console;

        static {
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                // prevent jline from being used if we are the first console user
                String res = System.getProperty("jdk.console");
                if (res == null) {
                    System.setProperty("jdk.console", "java.base");
                }
                return null;
            });
            Console cons = System.console();
            console = cons == null ? null : cons.writer();
        }

        private ConsoleHolder() {
        }
    }
}
