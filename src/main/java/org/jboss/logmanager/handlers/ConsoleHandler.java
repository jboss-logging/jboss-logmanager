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

import java.io.OutputStream;
import java.util.EnumMap;

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
    }

    private static final EnumMap<Target, OutputStream> targets;

    static {
        final EnumMap<Target, OutputStream> map = new EnumMap<Target, OutputStream>(Target.class);
        map.put(Target.SYSTEM_ERR, err);
        map.put(Target.SYSTEM_OUT, out);
        targets = map;
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
        this(Target.SYSTEM_OUT, formatter);
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
        super(wrap(targets.get(target)), formatter);
    }

    /**
     * Set the target for this console handler.
     *
     * @param target the target to write to, or {@code null} to clear the target
     */
    public void setTarget(Target target) {
        setOutputStream(targets.get(target));
    }

    private static OutputStream wrap(final OutputStream outputStream) {
        return outputStream == null ?
                null :
                outputStream instanceof UncloseableOutputStream ?
                        outputStream :
                        new UncloseableOutputStream(outputStream);
    }

    /** {@inheritDoc} */
    public void setOutputStream(final OutputStream outputStream) {
        super.setOutputStream(wrap(outputStream));
    }
}
