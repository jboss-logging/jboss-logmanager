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

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Log only messages that fall within a level range.
 */
public final class LevelRangeFilter implements Filter {
    private final int min;
    private final int max;
    private final boolean minInclusive;
    private final boolean maxInclusive;

    /**
     * Create a new instance.
     *
     * @param min the minimum (least severe) level, inclusive
     * @param minInclusive {@code true} if the {@code min} value is inclusive, {@code false} if it is exclusive
     * @param max the maximum (most severe) level, inclusive
     * @param maxInclusive {@code true} if the {@code max} value is inclusive, {@code false} if it is exclusive
     */
    public LevelRangeFilter(final Level min, final boolean minInclusive, final Level max, final boolean maxInclusive) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
        this.min = min.intValue();
        this.max = max.intValue();
        if (this.max < this.min) {
            throw new IllegalArgumentException("Max level cannot be less than min level");
        }
    }

    /**
     * Determine if a record is loggable.
     *
     * @param record the log record
     * @return {@code true} if the record's level falls within the range specified for this instance
     */
    public boolean isLoggable(final LogRecord record) {
        final int iv = record.getLevel().intValue();
        return (minInclusive ? min <= iv : min < iv) && (maxInclusive ? iv <= max : iv < max);
    }
}
