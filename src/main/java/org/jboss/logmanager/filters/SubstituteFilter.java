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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * A filter which applies a text substitution on the message if the nested filter matches.
 */
public final class SubstituteFilter implements Filter {

    private final Pattern pattern;
    private final String replacement;
    private final boolean replaceAll;

    /**
     * Construct a new instance.
     *
     * @param pattern the pattern to match
     * @param replacement the string replacement
     * @param replaceAll {@code true} if all occurrances should be replaced; {@code false} if only the first occurrance
     */
    public SubstituteFilter(final Pattern pattern, final String replacement, final boolean replaceAll) {
        this.pattern = pattern;
        this.replacement = replacement;
        this.replaceAll = replaceAll;
    }

    /**
     * Apply the filter to the given log record.
     *
     * @param record the log record to inspect and modify
     * @return {@code true} always
     */
    public boolean isLoggable(final LogRecord record) {
        final Matcher matcher = pattern.matcher(record.getMessage());
        if (replaceAll) {
            record.setMessage(matcher.replaceAll(replacement));
        } else {
            record.setMessage(matcher.replaceFirst(replacement));
        }
        return true;
    }
}
