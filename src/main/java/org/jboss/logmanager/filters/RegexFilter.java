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

package org.jboss.logmanager.filters;

import java.util.regex.Pattern;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * A regular-expression-based filter.  Used to exclude log records which match or don't match the expression.  The
 * regular expression is checked against the raw (unformatted) message.
 */
public final class RegexFilter implements Filter {
    private final Pattern pattern;
    private final boolean exclude;

    /**
     * Create a new instance.
     *
     * @param pattern the pattern to match
     * @param exclude {@code true} if matching records should be excluded, {@code false} if matching records should be included
     */
    public RegexFilter(final Pattern pattern, final boolean exclude) {
        this.pattern = pattern;
        this.exclude = exclude;
    }

    /**
     * Create a new instance.
     *
     * @param patternString the pattern string to match
     * @param exclude {@code true} if matching records should be excluded, {@code false} if matching records should be included
     */
    public RegexFilter(final String patternString, final boolean exclude) {
        this(Pattern.compile(patternString), exclude);
    }

    /**
     * Determine if this log record is loggable.
     *
     * @param record the log record
     * @return {@code true} if the log record is loggable
     */
    public boolean isLoggable(final LogRecord record) {
        return pattern.matcher(record.getMessage()).find() != exclude;
    }
}
