/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Inc., and individual contributors
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

import java.io.Console;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;

import java.util.logging.Formatter;

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

    private static final PrintWriter console;

    static {
        final Console con = System.console();
        console = con == null ? null : con.writer();
    }

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
        this(Target.CONSOLE, formatter);
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
     * @param target the target to write to, or {@code null} to start with an uninitialized target
     * @param formatter the formatter to use
     */
    public ConsoleHandler(final Target target, final Formatter formatter) {
        super(formatter);
        switch (target) {
            case SYSTEM_OUT: setOutputStream(wrap(out)); break;
            case SYSTEM_ERR: setOutputStream(wrap(err)); break;
            case CONSOLE: setWriter(wrap(console)); break;
            default: throw new IllegalArgumentException();
        }
    }

    /**
     * Set the target for this console handler.
     *
     * @param target the target to write to, or {@code null} to clear the target
     */
    public void setTarget(Target target) {
        switch (target) {
            case SYSTEM_OUT: setOutputStream(wrap(out)); break;
            case SYSTEM_ERR: setOutputStream(wrap(err)); break;
            case CONSOLE: setWriter(wrap(console)); break;
            default: throw new IllegalArgumentException();
        }
    }

    private static OutputStream wrap(final OutputStream outputStream) {
        return outputStream == null ?
                null :
                outputStream instanceof UncloseableOutputStream ?
                        outputStream :
                        new UncloseableOutputStream(outputStream);
    }

    private static Writer wrap(final Writer writer) {
        return writer == null ?
                null :
                writer instanceof UncloseableWriter ?
                        writer :
                        new UncloseableWriter(writer);
    }

    /** {@inheritDoc} */
    public void setOutputStream(final OutputStream outputStream) {
        super.setOutputStream(wrap(outputStream));
    }
}
