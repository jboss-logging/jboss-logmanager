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

package org.jboss.logmanager;

import java.io.Serializable;

/**
 * A marker class for loggers.  After read, the {@link #readResolve()} method will return a logger with the given name.
 */
public final class SerializedLogger implements Serializable {

    private static final long serialVersionUID = 8266206989821750874L;

    private final String name;

    /**
     * Construct an instance.
     *
     * @param name the logger name
     */
    public SerializedLogger(final String name) {
        this.name = name;
    }

    /**
     * Get the actual logger for this marker.
     *
     * @return the logger
     * @see <a href="http://java.sun.com/javase/6/docs/platform/serialization/spec/input.html#5903">Serialization spec, 3.7</a>
     */
    public Object readResolve() {
        return java.util.logging.Logger.getLogger(name);
    }
}
