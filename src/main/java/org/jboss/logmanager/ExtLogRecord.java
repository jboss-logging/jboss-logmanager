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

import java.util.Map;
import java.io.ObjectOutputStream;
import java.io.IOException;

import java.util.logging.LogRecord;

/**
 * An extended log record, which includes additional information including MDC/NDC and correct
 * caller location (even in the presence of a logging facade).
 */
public class ExtLogRecord extends LogRecord {

    private static final long serialVersionUID = -9174374711278052369L;

    /**
     * Construct a new instance.  Grabs the current NDC immediately.  MDC is deferred.
     *
     * @param level a logging level value
     * @param msg the raw non-localized logging message (may be null)
     * @param loggerClassName the name of the logger class
     */
    public ExtLogRecord(final java.util.logging.Level level, final String msg, final String loggerClassName) {
        super(level, msg);
        this.loggerClassName = loggerClassName;
        ndc = NDC.get();
        setUnknownCaller();
    }

    /**
     * Construct a new instance by copying an original record.
     *
     * @param original the record to copy
     */
    public ExtLogRecord(final LogRecord original, final String loggerClassName) {
        this(original.getLevel(), original.getMessage(), loggerClassName);
        setLoggerName(original.getLoggerName());
        setMillis(original.getMillis());
        setParameters(original.getParameters());
        setResourceBundle(original.getResourceBundle());
        setResourceBundleName(original.getResourceBundleName());
        setSequenceNumber(original.getSequenceNumber());
        // todo - find a way to not infer caller info if not already inferred?
        final String originalSourceClassName = original.getSourceClassName();
        if (originalSourceClassName != null) setSourceClassName(originalSourceClassName);
        final String originalSourceMethodName = original.getSourceMethodName();
        if (originalSourceMethodName != null) setSourceMethodName(originalSourceMethodName);
        // todo sourceLineNumber
        setThreadID(original.getThreadID());
        setThrown(original.getThrown());
    }

    private final String ndc;
    private transient final String loggerClassName;
    private transient boolean calculateCaller = true;

    private Map<String, String> mdcCopy;
    private int sourceLineNumber = -1;

    private void writeObject(ObjectOutputStream oos) throws IOException {
        copyMdc();
        calculateCaller();
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
        return mdcCopy != null ? mdcCopy.get(key) : MDC.get(key);
    }

    /**
     * Get the NDC for this log record.
     *
     * @return the NDC
     */
    public String getNdc() {
        return ndc;
    }

    /**
     * Get the class name of the logger which created this record.
     *
     * @return the class name
     */
    public String getLoggerClassName() {
        return loggerClassName;
    }

    /**
     * Find the first stack frame below the call to the logger, and populate the log record with that information.
     */
    private void calculateCaller() {
        if (! calculateCaller) {
            return;
        }
        calculateCaller = false;
        final StackTraceElement[] stack = new Throwable().getStackTrace();
        boolean found = false;
        for (StackTraceElement element : stack) {
            final String className = element.getClassName();
            if (found && ! loggerClassName.equals(className)) {
                setSourceClassName(className);
                setSourceMethodName(element.getMethodName());
                setSourceLineNumber(element.getLineNumber());
                return;
            } else {
                found = loggerClassName.equals(className);
            }
        }
        setUnknownCaller();
    }

    private void setUnknownCaller() {
        setSourceClassName("<unknown>");
        setSourceMethodName("<unknown>");
        setSourceLineNumber(-1);
    }

    /**
     * Get the source line number for this log record.
     * <p/>
     * Note that this sourceLineNumber is not verified and may be spoofed. This information may either have been
     * provided as part of the logging call, or it may have been inferred automatically by the logging framework. In the
     * latter case, the information may only be approximate and may in fact describe an earlier call on the stack frame.
     * May be -1 if no information could be obtained.
     *
     * @return the source line number
     */
    public int getSourceLineNumber() {
        calculateCaller();
        return sourceLineNumber;
    }

    /**
     * Set the source line number for this log record.
     *
     * @param sourceLineNumber the source line number
     */
    public void setSourceLineNumber(final int sourceLineNumber) {
        calculateCaller = false;
        this.sourceLineNumber = sourceLineNumber;
    }

    /** {@inheritDoc} */
    public String getSourceClassName() {
        calculateCaller();
        return super.getSourceClassName();
    }

    /** {@inheritDoc} */
    public void setSourceClassName(final String sourceClassName) {
        calculateCaller = false;
        super.setSourceClassName(sourceClassName);
    }

    /** {@inheritDoc} */
    public String getSourceMethodName() {
        calculateCaller();
        return super.getSourceMethodName();
    }

    /** {@inheritDoc} */
    public void setSourceMethodName(final String sourceMethodName) {
        calculateCaller = false;
        super.setSourceMethodName(sourceMethodName);
    }
}
