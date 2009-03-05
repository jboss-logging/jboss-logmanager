/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import java.util.Map;
import java.io.ObjectOutputStream;
import java.io.IOException;

import java.util.logging.LogRecord;

/**
 *
 */
public class ExtLogRecord extends LogRecord {

    private static final long serialVersionUID = -9174374711278052369L;

    /**
     * Construct a new instance.  Grabs the current NDC immediately.  MDC is deferred.
     *
     * @param level a logging level value
     * @param msg the raw non-localized logging message (may be null)
     */
    public ExtLogRecord(java.util.logging.Level level, String msg) {
        super(level, msg);
        ndc = NDC.get();
    }

    private Map<String, String> mdcCopy;
    private String ndc;

    private void writeObject(ObjectOutputStream oos) throws IOException {
        copyMdc();
        oos.defaultWriteObject();
    }

    /**
     * Copy the MDC.  Call this method before passing this log record to another thread.  Calling this method
     * more than once has no additional effect and will not incur extra copies.
     */
    public void copyMdc() {
        if (mdcCopy == null) {
            mdcCopy = MDC.copy();
        }
    }

    /**
     * Get the value of an MDC property.
     *
     * @param key the property key
     * @return the property value
     */
    public String getMdc(String key) {
        final Map<String, String> mdcCopy = this.mdcCopy;
        if (mdcCopy == null) {
            return MDC.get(key);
        } else {
            return mdcCopy.get(key);
        }
    }

    public String getNdc() {
        return ndc;
    }
}
