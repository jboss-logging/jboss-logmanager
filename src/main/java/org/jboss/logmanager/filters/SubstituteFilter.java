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

package org.jboss.logmanager.filters;

import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.logging.Filter;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.ExtLogRecord.FormatStyle;

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
     * Construct a new instance.
     *
     * @param patternString the pattern to match
     * @param replacement the string replacement
     * @param replaceAll {@code true} if all occurrances should be replaced; {@code false} if only the first occurrance
     */
    public SubstituteFilter(final String patternString, final String replacement, final boolean replaceAll) {
        this(Pattern.compile(patternString), replacement, replaceAll);
    }

    /**
     * Apply the filter to the given log record.
     * <p/>
     * The {@link FormatStyle format style} will always be set to {@link FormatStyle#NO_FORMAT} as the formatted
     * message will be the one used in the replacement.
     *
     * @param record the log record to inspect and modify
     *
     * @return {@code true} always
     */
    @Override
    public boolean isLoggable(final LogRecord record) {
        final Matcher matcher;
        if (record instanceof ExtLogRecord) {
            matcher = pattern.matcher(((ExtLogRecord) record).getFormattedMessage());
        } else {
            matcher = pattern.matcher(record.getMessage());
        }
        final String msg;
        if (replaceAll) {
            msg = matcher.replaceAll(replacement);
        } else {
            msg = matcher.replaceFirst(replacement);
        }
        if (record instanceof ExtLogRecord) {
            ((ExtLogRecord) record).setMessage(msg, FormatStyle.NO_FORMAT);
        } else {
            record.setMessage(msg);
        }
        return true;
    }
}
