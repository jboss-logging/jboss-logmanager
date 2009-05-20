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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A writer which sends its data to a logger.
 */
public final class LoggingWriter extends AbstractLoggingWriter {

    @SuppressWarnings({ "NonConstantLogger" })
    private final Logger log;
    private final Level level;

    /**
     * Construct a new instance.
     *
     * @param category the log category to use
     * @param level the level at which to log messages
     */
    public LoggingWriter(final String category, final Level level) {
        this.level = level;
        log = Logger.getLogger(category);
    }

    /**
     * Construct a new instance.
     *
     * @param log the logger to use
     * @param level the level at which to log messages
     */
    public LoggingWriter(final Logger log, final Level level) {
        this.log = log;
        this.level = level;
    }

    /** {@inheritDoc} */
    protected Logger getLogger() {
        return log;
    }

    /** {@inheritDoc} */
    protected Level getLevel() {
        return level;
    }
}
