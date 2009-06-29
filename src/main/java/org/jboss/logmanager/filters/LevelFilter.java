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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A filter which excludes messages of a certain level or levels
 */
public final class LevelFilter implements Filter {
    private final Set<Level> includedLevels;

    /**
     * Construct a new instance.
     *
     * @param includedLevel the level to include
     */
    public LevelFilter(final Level includedLevel) {
        includedLevels = Collections.singleton(includedLevel);
    }

    /**
     * Construct a new instance.
     *
     * @param includedLevels the levels to exclude
     */
    public LevelFilter(final Collection<Level> includedLevels) {
        this.includedLevels = new HashSet<Level>(includedLevels);
    }

    /**
     * Determine whether the message is loggable.
     *
     * @param record the log record
     * @return {@code true} if the level is in the inclusion list
     */
    public boolean isLoggable(final LogRecord record) {
        return includedLevels.contains(record.getLevel());
    }
}