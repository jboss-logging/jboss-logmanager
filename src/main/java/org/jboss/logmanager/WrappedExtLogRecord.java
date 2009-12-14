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

import java.util.ResourceBundle;

import java.util.logging.Level;
import java.util.logging.LogRecord;

class WrappedExtLogRecord extends ExtLogRecord {

    private static final long serialVersionUID = 980830752574061944L;
    private static final String LOGGER_CLASS_NAME = java.util.logging.Logger.class.getName();

    private final LogRecord orig;

    WrappedExtLogRecord(final LogRecord orig) {
        super(orig.getLevel(), orig.getMessage(), LOGGER_CLASS_NAME);
        this.orig = orig;
    }

    public String getLoggerName() {
        return orig.getLoggerName();
    }

    public void setLoggerName(final String name) {
        super.setLoggerName(name);
        orig.setLoggerName(name);
    }

    public ResourceBundle getResourceBundle() {
        return orig.getResourceBundle();
    }

    public void setResourceBundle(final ResourceBundle bundle) {
        super.setResourceBundle(bundle);
        orig.setResourceBundle(bundle);
    }

    public String getResourceBundleName() {
        return orig.getResourceBundleName();
    }

    public void setResourceBundleName(final String name) {
        super.setResourceBundleName(name);
        orig.setResourceBundleName(name);
    }

    public Level getLevel() {
        return orig.getLevel();
    }

    public void setLevel(final Level level) {
        super.setLevel(level);
        orig.setLevel(level);
    }

    public long getSequenceNumber() {
        return orig.getSequenceNumber();
    }

    public void setSequenceNumber(final long seq) {
        super.setSequenceNumber(seq);
        orig.setSequenceNumber(seq);
    }

    public String getSourceClassName() {
        return orig.getSourceClassName();
    }

    public void setSourceClassName(final String sourceClassName) {
        super.setSourceClassName(sourceClassName);
        orig.setSourceClassName(sourceClassName);
    }

    public String getSourceMethodName() {
        return orig.getSourceMethodName();
    }

    public void setSourceMethodName(final String sourceMethodName) {
        super.setSourceMethodName(sourceMethodName);
        orig.setSourceMethodName(sourceMethodName);
    }

    public String getMessage() {
        return orig.getMessage();
    }

    public void setMessage(final String message) {
        super.setMessage(message);
        orig.setMessage(message);
    }

    public Object[] getParameters() {
        return orig.getParameters();
    }

    public void setParameters(final Object[] parameters) {
        orig.setParameters(parameters);
    }

    public int getThreadID() {
        return orig.getThreadID();
    }

    public void setThreadID(final int threadID) {
        orig.setThreadID(threadID);
    }

    public long getMillis() {
        return orig.getMillis();
    }

    public void setMillis(final long millis) {
        orig.setMillis(millis);
    }

    public Throwable getThrown() {
        return orig.getThrown();
    }

    public void setThrown(final Throwable thrown) {
        orig.setThrown(thrown);
    }

    protected Object writeReplace() {
        return new ExtLogRecord(this);
    }
}
