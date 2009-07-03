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

package org.jboss.logmanager.handlers;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;

import java.util.logging.Handler;

/**
 * A handler which wraps other handlers, forcing a flush after each successful message publication.
 */
public class AutoFlushingHandler extends ExtHandler {

    /**
     * Construct a new instance.
     */
    public AutoFlushingHandler() {
    }

    /** {@inheritDoc} */
    public void publish(final ExtLogRecord record) {
        for (Handler handler : handlers) {
            handler.publish(record);
            handler.flush();
        }
    }

    /** {@inheritDoc} */
    public void flush() {
        for (Handler handler : handlers) {
            handler.flush();
        }
    }

    /** {@inheritDoc}  This implementation does nothing. */
    public void close() throws SecurityException {
    }
}
