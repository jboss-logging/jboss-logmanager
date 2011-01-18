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
 * A filter which modifies the log record with a new level if the nested filter evaluates {@code true} for that
 * record.
 */
public final class LevelChangingFilter implements Filter {

    private final Level newLevel;

    /**
     * Construct a new instance.
     *
     * @param newLevel the level to change to
     */
    public LevelChangingFilter(final Level newLevel) {
        this.newLevel = newLevel;
    }

    /**
     * Apply the filter to this log record.
     *
     * @param record the record to inspect and possibly update
     * @return {@code true} always
     */
    public boolean isLoggable(final LogRecord record) {
        record.setLevel(newLevel);
        return true;
    }
}
