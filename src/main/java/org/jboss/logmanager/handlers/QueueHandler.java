/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat Inc., and individual contributors
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

import java.util.ArrayDeque;
import java.util.Deque;
import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;

import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;

/**
 * A queue handler which retains the last few messages logged.  The handler can be used as-is to remember recent
 * messages, or one or more handlers may be nested, which allows this handler to "replay" messages to the child
 * handler(s) upon request.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class QueueHandler extends ExtHandler {
    private final Deque<ExtLogRecord> buffer = new ArrayDeque<ExtLogRecord>();
    private int limit = 10;

    protected void doPublish(final ExtLogRecord record) {
        record.copyAll();
        synchronized (buffer) {
            if (buffer.size() == limit) { buffer.removeFirst(); }
            buffer.addLast(record);
            for (Handler handler : getHandlers()) {
                handler.publish(record);
            }
        }
    }

    /**
     * Get a copy of the queue as it is at an exact moment in time.
     *
     * @return the copy of the queue
     */
    public ExtLogRecord[] getQueue() {
        synchronized (buffer) {
            return buffer.toArray(new ExtLogRecord[buffer.size()]);
        }
    }

    /**
     * Get a copy of the queue, rendering each record as a string.
     *
     * @return the copy of the queue rendered as strings
     */
    public String[] getQueueAsStrings() {
        final ExtLogRecord[] queue = getQueue();
        final int length = queue.length;
        final String[] strings = new String[length];
        final Formatter formatter = getFormatter();
        for (int i = 0, j = 0; j < length; j++) {
            final String formatted;
            try {
                formatted = formatter.format(queue[j]);
                if (formatted.length() > 0) {
                    strings[i++] = getFormatter().format(queue[j]);
                }
            } catch (Exception ex) {
                reportError("Formatting error", ex, ErrorManager.FORMAT_FAILURE);
            }
        }
        return strings;
    }

    /**
     * Replay the stored queue to the nested handlers.
     */
    public void replay() {
        final Handler[] handlers = getHandlers();
        if (handlers.length > 0) for (ExtLogRecord record : getQueue()) {
            for (Handler handler : handlers) {
                handler.publish(record);
            }
        }
    }
}
