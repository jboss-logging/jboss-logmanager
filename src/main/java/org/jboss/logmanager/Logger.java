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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.Lock;

import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.slf4j.spi.LocationAwareLogger;
import org.slf4j.Marker;

/**
 * An actual logger instance.  This is the end-user interface into the logging system.
 */
public class Logger extends java.util.logging.Logger implements LocationAwareLogger {

    /**
     * The named logger tree node.
     */
    private final LoggerNode loggerNode;

    /**
     * The handlers for this logger.  May only be updated using the {@link #handlersUpdater} atomic updater.  The array
     * instance should not be modified (treat as immutable).
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    private volatile Handler[] handlers;

    /**
     * Flag to specify whether parent handlers are used.
     */
    private volatile boolean useParentHandlers = true;

    /**
     * The filter for this logger instance.
     */
    private volatile Filter filter;

    /**
     * The atomic updater for the {@link #handlers} field.
     */
    private static final AtomicReferenceFieldUpdater<Logger, Handler[]> handlersUpdater = AtomicReferenceFieldUpdater.newUpdater(Logger.class, Handler[].class, "handlers");

    /**
     * The empty handler list.
     */
    private static final Handler[] EMPTY_HANDLERS = new Handler[0];
    private static final String LOGGER_CLASS_NAME = Logger.class.getName();

    /**
     * Static logger factory method which returns a JBoss LogManager logger.
     *
     * @param name the logger name
     * @return the logger
     */
    public static Logger getLogger(final String name) {
        return LogContext.getLogContext().getLogger(name);
    }

    /**
     * Construct a new instance of an actual logger.
     *
     * @param loggerNode the node in the named logger tree
     * @param name the fully-qualified name of this node
     */
    Logger(final LoggerNode loggerNode, final String name) {
        // Logger.getLogger(*) will set up the resource bundle for us, how kind
        super(name, null);
        // We maintain our own level
        super.setLevel(Level.ALL);
        this.loggerNode = loggerNode;
    }
    // Filter mgmt

    /** {@inheritDoc} */
    public void setFilter(Filter filter) throws SecurityException {
        LogContext.checkAccess();
        this.filter = filter;
    }

    /** {@inheritDoc} */
    public Filter getFilter() {
        return filter;
    }

    // Level mgmt

    /**
     * The actual level.  May only be modified when the logmanager's level change lock is held; in addition, changing
     * this field must be followed immediately by recursively updating the effective loglevel of the child tree.
     */
    private volatile Level level;
    /**
     * The effective level.  May only be modified when the logmanager's level change lock is held; in addition, changing
     * this field must be followed immediately by recursively updating the effective loglevel of the child tree.
     */
    private volatile int effectiveLevel = INFO_INT;

    /**
     * {@inheritDoc}  This implementation grabs a lock, so that only one thread may update the log level of any
     * logger at a time, in order to allow readers to never block (though there is a window where retrieving the
     * log level reflects an older effective level than the actual level).
     */
    public void setLevel(Level newLevel) throws SecurityException {
        final LogContext context = loggerNode.getContext();
        LogContext.checkAccess();
        final Lock lock = context.treeLock;
        lock.lock();
        try {
            final int oldEffectiveLevel = effectiveLevel;
            final int newEffectiveLevel;
            if (newLevel != null) {
                level = newLevel;
                newEffectiveLevel = newLevel.intValue();
            } else {
                final Logger parent = (Logger) getParent();
                if (parent == null) {
                    level = Level.INFO;
                    newEffectiveLevel = INFO_INT;
                } else {
                    level = null;
                    newEffectiveLevel = parent.effectiveLevel;
                }
            }
            effectiveLevel = newEffectiveLevel;
            if (oldEffectiveLevel != newEffectiveLevel) {
                // our level changed, recurse down to children
                loggerNode.updateChildEffectiveLevel(newEffectiveLevel);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Update the effective level if it is inherited from a parent.  Must only be called while the logmanager's level
     * change lock is held.
     *
     * @param newLevel the new effective level
     */
    void setEffectiveLevel(int newLevel) {
        if (level == null) {
            effectiveLevel = newLevel;
            loggerNode.updateChildEffectiveLevel(newLevel);
        }
    }

    /** {@inheritDoc} */
    public Level getLevel() {
        return level;
    }

    /** {@inheritDoc} */
    public boolean isLoggable(Level level) {
        final int effectiveLevel = this.effectiveLevel;
        return level.intValue() >= effectiveLevel && effectiveLevel != OFF_INT;
    }

    // Handler mgmt

    /** {@inheritDoc} */
    public void addHandler(Handler handler) throws SecurityException {
        LogContext.checkAccess();
        boolean ok;
        do {
            final Handler[] oldHandlers = handlers;
            final Handler[] newHandlers;
            if (oldHandlers != null) {
                final int len = oldHandlers.length;
                newHandlers = new Handler[len + 1];
                System.arraycopy(oldHandlers, 0, newHandlers, 0, len);
            } else {
                newHandlers = new Handler[] { handler };
            }
            ok = handlersUpdater.compareAndSet(this, oldHandlers, newHandlers);
        } while (! ok);
    }

    /** {@inheritDoc} */
    public void removeHandler(Handler handler) throws SecurityException {
        LogContext.checkAccess();
        boolean ok;
        do {
            final Handler[] oldHandlers = handlers;
            final Handler[] newHandlers;
            final int len = oldHandlers.length;
            if (len == 0) {
                return;
            } else if (len == 1) {
                if (oldHandlers[0] == handler) {
                    newHandlers = null;
                } else {
                    return;
                }
            } else {
                boolean found = false;
                newHandlers = new Handler[len - 1];
                for (int i = 0, j = 0; i < oldHandlers.length; i++) {
                    Handler oldHandler = oldHandlers[i];
                    if (handler != oldHandler) {
                        newHandlers[j++] = oldHandler;
                    } else {
                        found = true;
                    }
                }
                if (! found) {
                    return;
                }
                System.arraycopy(oldHandlers, 0, newHandlers, 0, len);
            }
            ok = handlersUpdater.compareAndSet(this, oldHandlers, newHandlers);
        } while (! ok);
    }

    /** {@inheritDoc} */
    public Handler[] getHandlers() {
        final Handler[] handlers = this.handlers;
        return handlers == null ? EMPTY_HANDLERS : handlers.clone();
    }

    /**
     * A convenience method to quickly clear all handlers.
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code LoggingPermission(control)}
     */
    public void clearHandlers() throws SecurityException {
        LogContext.checkAccess();
        handlersUpdater.set(this, null);
    }

    /** {@inheritDoc} */
    public void setUseParentHandlers(boolean useParentHandlers) {
        this.useParentHandlers = useParentHandlers;
    }

    /** {@inheritDoc} */
    public boolean getUseParentHandlers() {
        return useParentHandlers;
    }

    // Parent/child

    /** {@inheritDoc} */
    public Logger getParent() {
        return loggerNode.getParentLogger();
    }

    /**
     * <b>Not allowed.</b>  This method may never be called.
     * @throws SecurityException always
     */
    public void setParent(java.util.logging.Logger parent) {
        throw new SecurityException("setParent() disallowed");
    }

    // Logger

    /**
     * Do the logging with no level checks (they've already been done).
     *
     * @param record the log record
     */
    private void doLog(final LogRecord record) {
        final ExtLogRecord extRecord = (record instanceof ExtLogRecord) ? (ExtLogRecord) record : new ExtLogRecord(record, LOGGER_CLASS_NAME);
        // todo - resource bundle
        extRecord.setLoggerName(getName());
        final Filter filter = this.filter;
        try {
            if (filter != null && ! filter.isLoggable(extRecord)) {
                return;
            }
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            // todo - error handler
            // treat an errored filter as "pass" (I guess?)
        }
        for (Logger current = this; current != null; current = current.getParent()) {
            final Handler[] handlers = current.handlers;
            if (handlers != null) {
                for (Handler handler : handlers) try {
                    handler.publish(extRecord);
                } catch (VirtualMachineError e) {
                    throw e;
                } catch (Throwable t) {
                    // todo - error handler
                }
            }
            if (! current.useParentHandlers) {
                break;
            }
        }
    }

    private static final int OFF_INT = Level.OFF.intValue();

    private static final int SEVERE_INT = Level.SEVERE.intValue();
    private static final int WARNING_INT = Level.WARNING.intValue();
    private static final int INFO_INT = Level.INFO.intValue();
    private static final int CONFIG_INT = Level.CONFIG.intValue();
    private static final int FINE_INT = Level.FINE.intValue();
    private static final int FINER_INT = Level.FINER.intValue();
    private static final int FINEST_INT = Level.FINEST.intValue();

    private static final int ALT_ERROR_INT = org.jboss.logmanager.Level.ERROR.intValue();
    private static final int ALT_WARN_INT = org.jboss.logmanager.Level.WARN.intValue();
    private static final int ALT_INFO_INT = org.jboss.logmanager.Level.INFO.intValue();
    private static final int ALT_DEBUG_INT = org.jboss.logmanager.Level.DEBUG.intValue();
    private static final int ALT_TRACE_INT = org.jboss.logmanager.Level.TRACE.intValue();

    /** {@inheritDoc} */
    public void log(LogRecord record) {
        final int effectiveLevel = this.effectiveLevel;
        if (record.getLevel().intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        doLog(record);
    }

    public void entering(final String sourceClass, final String sourceMethod) {
        if (FINER_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "ENTRY", LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        doLog(rec);
    }

    public void entering(final String sourceClass, final String sourceMethod, final Object param1) {
        if (FINER_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "ENTRY {0}", LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setParameters(new Object[] { param1 });
        doLog(rec);
    }

    public void entering(final String sourceClass, final String sourceMethod, final Object[] params) {
        if (FINER_INT < effectiveLevel) {
            return;
        }
        final StringBuilder builder = new StringBuilder("ENTRY");
        for (int i = 0; i < params.length; i++) {
            builder.append(" {").append(i).append('}');
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, builder.toString(), LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        if (params != null) rec.setParameters(params);
        doLog(rec);
    }

    public void exiting(final String sourceClass, final String sourceMethod) {
        if (FINER_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "RETURN", LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        doLog(rec);
    }

    public void exiting(final String sourceClass, final String sourceMethod, final Object result) {
        if (FINER_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "RETURN {0}", LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setParameters(new Object[] { result });
        doLog(rec);
    }

    public void throwing(final String sourceClass, final String sourceMethod, final Throwable thrown) {
        if (FINER_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "THROW", LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setThrown(thrown);
        doLog(rec);
    }

    public void severe(final String msg) {
        if (SEVERE_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(Level.SEVERE, msg, LOGGER_CLASS_NAME));
    }

    public void warning(final String msg) {
        if (WARNING_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(Level.WARNING, msg, LOGGER_CLASS_NAME));
    }

    public void info(final String msg) {
        if (INFO_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(Level.INFO, msg, LOGGER_CLASS_NAME));
    }

    public void config(final String msg) {
        if (CONFIG_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(Level.CONFIG, msg, LOGGER_CLASS_NAME));
    }

    public void fine(final String msg) {
        if (FINE_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(Level.FINE, msg, LOGGER_CLASS_NAME));
    }

    public void finer(final String msg) {
        if (FINER_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(Level.FINER, msg, LOGGER_CLASS_NAME));
    }

    public void finest(final String msg) {
        if (FINEST_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(Level.FINEST, msg, LOGGER_CLASS_NAME));
    }

    public void log(final Level level, final String msg) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        doLog(new ExtLogRecord(level, msg, LOGGER_CLASS_NAME));
    }

    public void log(final Level level, final String msg, final Object param1) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { param1 });
        doLog(rec);
    }

    public void log(final Level level, final String msg, final Object[] params) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        if (params != null) rec.setParameters(params);
        doLog(rec);
    }

    public void log(final Level level, final String msg, final Throwable thrown) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setThrown(thrown);
        doLog(rec);
    }

    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        doLog(rec);
    }

    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg, final Object param1) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setParameters(new Object[] { param1 });
        doLog(rec);
    }

    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg, final Object[] params) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        if (params != null) rec.setParameters(params);
        doLog(rec);
    }

    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg, final Throwable thrown) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setThrown(thrown);
        doLog(rec);
    }

    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setResourceBundleName(bundleName);
        doLog(rec);
    }

    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg, final Object param1) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setResourceBundleName(bundleName);
        rec.setParameters(new Object[] { param1 });
        doLog(rec);
    }

    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg, final Object[] params) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setResourceBundleName(bundleName);
        if (params != null) rec.setParameters(params);
        doLog(rec);
    }

    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg, final Throwable thrown) {
        final int effectiveLevel = this.effectiveLevel;
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setResourceBundleName(bundleName);
        rec.setThrown(thrown);
        doLog(rec);
    }

    // GC

    /**
     * Perform finalization actions.  This amounts to clearing out the loglevel so that all children are updated
     * with the parent's effective loglevel.  As such, a lock is acquired from this method which might cause delays in
     * garbage collection.
     */
    protected void finalize() throws Throwable {
        try {
            // clear out level so that it spams out to all children
            setLevel(null);
        } finally {
            super.finalize();
        }
    }

    // slf4j implementation

    public void log(final Marker marker, final String fqcn, final int levelVal, final String message, final Throwable t) {
        // ignore marker
        final Level level;
        switch (levelVal) {
            case LocationAwareLogger.TRACE_INT: level = org.jboss.logmanager.Level.TRACE; break;
            case LocationAwareLogger.DEBUG_INT: level = org.jboss.logmanager.Level.DEBUG; break;
            case LocationAwareLogger.INFO_INT: level = org.jboss.logmanager.Level.INFO; break;
            case LocationAwareLogger.WARN_INT: level = org.jboss.logmanager.Level.WARN; break;
            case LocationAwareLogger.ERROR_INT: level = org.jboss.logmanager.Level.ERROR; break;
            default: level = org.jboss.logmanager.Level.DEBUG; break;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, message, fqcn);
        rec.setThrown(t);
        doLog(rec);
    }

    public boolean isTraceEnabled() {
        return ALT_TRACE_INT < effectiveLevel;
    }

    public void trace(final String msg) {
        if (ALT_TRACE_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(org.jboss.logmanager.Level.TRACE, msg, LOGGER_CLASS_NAME));
    }

    public void trace(final String format, final Object arg) {
        if (ALT_TRACE_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.TRACE, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg });
        doLog(rec);
    }

    public void trace(final String format, final Object arg1, final Object arg2) {
        if (ALT_TRACE_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.TRACE, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg1, arg2 });
        doLog(rec);
    }

    public void trace(final String format, final Object[] argArray) {
        if (ALT_TRACE_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.TRACE, format, LOGGER_CLASS_NAME);
        rec.setParameters(argArray);
        doLog(rec);
    }

    public void trace(final String msg, final Throwable t) {
        if (ALT_TRACE_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.TRACE, msg, LOGGER_CLASS_NAME);
        rec.setThrown(t);
        doLog(rec);
    }

    public boolean isTraceEnabled(final Marker marker) {
        return ALT_TRACE_INT < effectiveLevel;
    }

    public void trace(final Marker marker, final String msg) {
        if (ALT_TRACE_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(org.jboss.logmanager.Level.TRACE, msg, LOGGER_CLASS_NAME));
    }

    public void trace(final Marker marker, final String format, final Object arg) {
        if (ALT_TRACE_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.TRACE, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg });
        doLog(rec);
    }

    public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (ALT_TRACE_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.TRACE, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg1, arg2 });
        doLog(rec);
    }

    public void trace(final Marker marker, final String format, final Object[] argArray) {
        if (ALT_TRACE_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.TRACE, format, LOGGER_CLASS_NAME);
        rec.setParameters(argArray);
        doLog(rec);
    }

    public void trace(final Marker marker, final String msg, final Throwable t) {
        if (ALT_TRACE_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.TRACE, msg, LOGGER_CLASS_NAME);
        rec.setThrown(t);
        doLog(rec);
    }

    public boolean isDebugEnabled() {
        return ALT_DEBUG_INT < effectiveLevel;
    }

    public void debug(final String msg) {
        if (ALT_DEBUG_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(org.jboss.logmanager.Level.DEBUG, msg, LOGGER_CLASS_NAME));
    }

    public void debug(final String format, final Object arg) {
        if (ALT_DEBUG_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.DEBUG, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg });
        doLog(rec);
    }

    public void debug(final String format, final Object arg1, final Object arg2) {
        if (ALT_DEBUG_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.DEBUG, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg1, arg2 });
        doLog(rec);
    }

    public void debug(final String format, final Object[] argArray) {
        if (ALT_DEBUG_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.DEBUG, format, LOGGER_CLASS_NAME);
        rec.setParameters(argArray);
        doLog(rec);
    }

    public void debug(final String msg, final Throwable t) {
        if (ALT_DEBUG_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.DEBUG, msg, LOGGER_CLASS_NAME);
        rec.setThrown(t);
        doLog(rec);
    }

    public boolean isDebugEnabled(final Marker marker) {
        return ALT_DEBUG_INT < effectiveLevel;
    }

    public void debug(final Marker marker, final String msg) {
        if (ALT_DEBUG_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(org.jboss.logmanager.Level.DEBUG, msg, LOGGER_CLASS_NAME));
    }

    public void debug(final Marker marker, final String format, final Object arg) {
        if (ALT_DEBUG_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.DEBUG, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg });
        doLog(rec);
    }

    public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (ALT_DEBUG_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.DEBUG, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg1, arg2 });
        doLog(rec);
    }

    public void debug(final Marker marker, final String format, final Object[] argArray) {
        if (ALT_DEBUG_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.DEBUG, format, LOGGER_CLASS_NAME);
        rec.setParameters(argArray);
        doLog(rec);
    }

    public void debug(final Marker marker, final String msg, final Throwable t) {
        if (ALT_DEBUG_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.DEBUG, msg, LOGGER_CLASS_NAME);
        rec.setThrown(t);
        doLog(rec);
    }

    public boolean isInfoEnabled() {
        return ALT_INFO_INT < effectiveLevel;
    }

    // info(String) is defined above, and happens to be compatible

    public void info(final String format, final Object arg) {
        if (ALT_INFO_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.INFO, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg });
        doLog(rec);
    }

    public void info(final String format, final Object arg1, final Object arg2) {
        if (ALT_INFO_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.INFO, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg1, arg2 });
        doLog(rec);
    }

    public void info(final String format, final Object[] argArray) {
        if (ALT_INFO_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.INFO, format, LOGGER_CLASS_NAME);
        rec.setParameters(argArray);
        doLog(rec);
    }

    public void info(final String msg, final Throwable t) {
        if (ALT_INFO_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.INFO, msg, LOGGER_CLASS_NAME);
        rec.setThrown(t);
        doLog(rec);
    }

    public boolean isInfoEnabled(final Marker marker) {
        return ALT_INFO_INT < effectiveLevel;
    }

    public void info(final Marker marker, final String msg) {
        if (ALT_INFO_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(org.jboss.logmanager.Level.INFO, msg, LOGGER_CLASS_NAME));
    }

    public void info(final Marker marker, final String format, final Object arg) {
        if (ALT_INFO_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.INFO, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg });
        doLog(rec);
    }

    public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (ALT_INFO_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.INFO, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg1, arg2 });
        doLog(rec);
    }

    public void info(final Marker marker, final String format, final Object[] argArray) {
        if (ALT_INFO_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.INFO, format, LOGGER_CLASS_NAME);
        rec.setParameters(argArray);
        doLog(rec);
    }

    public void info(final Marker marker, final String msg, final Throwable t) {
        if (ALT_INFO_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.INFO, msg, LOGGER_CLASS_NAME);
        rec.setThrown(t);
        doLog(rec);
    }

    public boolean isWarnEnabled() {
        return ALT_WARN_INT < effectiveLevel;
    }

    public void warn(final String msg) {
        if (ALT_WARN_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(org.jboss.logmanager.Level.WARN, msg, LOGGER_CLASS_NAME));
    }

    public void warn(final String format, final Object arg) {
        if (ALT_WARN_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.WARN, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg });
        doLog(rec);
    }

    public void warn(final String format, final Object arg1, final Object arg2) {
        if (ALT_WARN_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.WARN, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg1, arg2 });
        doLog(rec);
    }

    public void warn(final String format, final Object[] argArray) {
        if (ALT_WARN_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.WARN, format, LOGGER_CLASS_NAME);
        rec.setParameters(argArray);
        doLog(rec);
    }

    public void warn(final String msg, final Throwable t) {
        if (ALT_WARN_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.WARN, msg, LOGGER_CLASS_NAME);
        rec.setThrown(t);
        doLog(rec);
    }

    public boolean isWarnEnabled(final Marker marker) {
        return ALT_WARN_INT < effectiveLevel;
    }

    public void warn(final Marker marker, final String msg) {
        if (ALT_WARN_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(org.jboss.logmanager.Level.WARN, msg, LOGGER_CLASS_NAME));
    }

    public void warn(final Marker marker, final String format, final Object arg) {
        if (ALT_WARN_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.WARN, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg });
        doLog(rec);
    }

    public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (ALT_WARN_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.WARN, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg1, arg2 });
        doLog(rec);
    }

    public void warn(final Marker marker, final String format, final Object[] argArray) {
        if (ALT_WARN_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.WARN, format, LOGGER_CLASS_NAME);
        rec.setParameters(argArray);
        doLog(rec);
    }

    public void warn(final Marker marker, final String msg, final Throwable t) {
        if (ALT_WARN_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.WARN, msg, LOGGER_CLASS_NAME);
        rec.setThrown(t);
        doLog(rec);
    }

    public boolean isErrorEnabled() {
        return ALT_ERROR_INT < effectiveLevel;
    }

    public void error(final String msg) {
        if (ALT_ERROR_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(org.jboss.logmanager.Level.ERROR, msg, LOGGER_CLASS_NAME));
    }

    public void error(final String format, final Object arg) {
        if (ALT_ERROR_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.ERROR, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg });
        doLog(rec);
    }

    public void error(final String format, final Object arg1, final Object arg2) {
        if (ALT_ERROR_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.ERROR, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg1, arg2 });
        doLog(rec);
    }

    public void error(final String format, final Object[] argArray) {
        if (ALT_ERROR_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.ERROR, format, LOGGER_CLASS_NAME);
        rec.setParameters(argArray);
        doLog(rec);
    }

    public void error(final String msg, final Throwable t) {
        if (ALT_ERROR_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.ERROR, msg, LOGGER_CLASS_NAME);
        rec.setThrown(t);
        doLog(rec);
    }

    public boolean isErrorEnabled(final Marker marker) {
        return ALT_ERROR_INT < effectiveLevel;
    }

    public void error(final Marker marker, final String msg) {
        if (ALT_ERROR_INT < effectiveLevel) {
            return;
        }
        doLog(new ExtLogRecord(org.jboss.logmanager.Level.ERROR, msg, LOGGER_CLASS_NAME));
    }

    public void error(final Marker marker, final String format, final Object arg) {
        if (ALT_ERROR_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.ERROR, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg });
        doLog(rec);
    }

    public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (ALT_ERROR_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.ERROR, format, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { arg1, arg2 });
        doLog(rec);
    }

    public void error(final Marker marker, final String format, final Object[] argArray) {
        if (ALT_ERROR_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.ERROR, format, LOGGER_CLASS_NAME);
        rec.setParameters(argArray);
        doLog(rec);
    }

    public void error(final Marker marker, final String msg, final Throwable t) {
        if (ALT_ERROR_INT < effectiveLevel) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(org.jboss.logmanager.Level.ERROR, msg, LOGGER_CLASS_NAME);
        rec.setThrown(t);
        doLog(rec);
    }

}
