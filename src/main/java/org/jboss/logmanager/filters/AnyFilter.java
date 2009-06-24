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

import java.util.Iterator;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * A filter consisting of several filters in a chain.  If any filter finds the log message to be loggable,
 * the message will be logged and subsequent filters will not be checked.  If there are no nested filters, this
 * instance always returns {@code false}.
 */
public final class AnyFilter implements Filter {
    private final Filter[] filters;

    /**
     * Construct a new instance.
     *
     * @param filters the constituent filters
     */
    public AnyFilter(final Filter[] filters) {
        this.filters = filters;
    }

    /**
     * Construct a new instance.
     *
     * @param filters the constituent filters
     */
    public AnyFilter(final Iterable<Filter> filters) {
        this(filters.iterator());
    }

    /**
     * Construct a new instance.
     *
     * @param filters the constituent filters
     */
    public AnyFilter(final Iterator<Filter> filters) {
        this.filters = unroll(filters, 0);
    }

    private static Filter[] unroll(Iterator<Filter> iter, int cnt) {
        if (iter.hasNext()) {
            final Filter filter = iter.next();
            if (filter == null) {
                throw new NullPointerException("filter at index " + cnt + " is null");
            }
            final Filter[] filters = unroll(iter, cnt + 1);
            filters[cnt] = filter;
            return filters;
        } else {
            return new Filter[cnt];
        }
    }

    /**
     * Determine whether the record is loggable.
     *
     * @param record the log record
     * @return {@code true} if any of the constituent filters return {@code true}
     */
    public boolean isLoggable(final LogRecord record) {
        for (Filter filter : filters) {
            if (filter.isLoggable(record)) {
                return true;
            }
        }
        return false;
    }
}