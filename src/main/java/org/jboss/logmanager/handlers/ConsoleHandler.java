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
        this(console == null ? Target.SYSTEM_OUT : Target.CONSOLE, formatter);
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
    @Override
    public void setOutputStream(final OutputStream outputStream) {
        super.setOutputStream(wrap(outputStream));
    }
}
