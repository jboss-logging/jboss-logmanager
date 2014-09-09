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

package org.jboss.logmanager;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.ResourceBundle;

import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * An actual logger instance.  This is the end-user interface into the logging system.
 */
@SuppressWarnings({ "SerializableClassWithUnconstructableAncestor" })
public final class Logger extends java.util.logging.Logger implements Serializable {

    private static final long serialVersionUID = 5093333069125075416L;

    /**
     * The named logger tree node.
     */
    private final LoggerNode loggerNode;

    private static final String LOGGER_CLASS_NAME = Logger.class.getName();

    /**
     * Static logger factory method which returns a JBoss LogManager logger.
     *
     * @param name the logger name
     * @return the logger
     */
    public static Logger getLogger(final String name) {
        try {
            // call through j.u.l.Logger so that primordial configuration is set up
            return (Logger) java.util.logging.Logger.getLogger(name);
        } catch (ClassCastException e) {
            throw new IllegalStateException("The LogManager was not properly installed (you must set the \"java.util.logging.manager\" system property to \"" + LogManager.class.getName() + "\")");
        }
    }

    /**
     * Static logger factory method which returns a JBoss LogManager logger.
     *
     * @param name the logger name
     * @param bundleName the bundle name
     * @return the logger
     */
    public static Logger getLogger(final String name, final String bundleName) {
        try {
            // call through j.u.l.Logger so that primordial configuration is set up
            return (Logger) java.util.logging.Logger.getLogger(name, bundleName);
        } catch (ClassCastException e) {
            throw new IllegalStateException("The LogManager was not properly installed (you must set the \"java.util.logging.manager\" system property to \"" + LogManager.class.getName() + "\")");
        }
    }

    /**
     * Construct a new instance of an actual logger.
     *
     * @param loggerNode the node in the named logger tree
     * @param name the fully-qualified name of this node
     */
    Logger(final LoggerNode loggerNode, final String name) {
        // Don't set up the bundle in the parent...
        super(name, null);
        // We maintain our own level
        super.setLevel(Level.ALL);
        this.loggerNode = loggerNode;
    }

    // Serialization

    protected final Object writeReplace() throws ObjectStreamException {
        return new SerializedLogger(getName());
    }

    // Filter mgmt

    /** {@inheritDoc} */
    public void setFilter(Filter filter) throws SecurityException {
        LogContext.checkAccess(loggerNode.getContext());
        loggerNode.setFilter(filter);
    }

    /** {@inheritDoc} */
    public Filter getFilter() {
        return loggerNode.getFilter();
    }

    // Level mgmt

    /**
     * {@inheritDoc}  This implementation grabs a lock, so that only one thread may update the log level of any
     * logger at a time, in order to allow readers to never block (though there is a window where retrieving the
     * log level reflects an older effective level than the actual level).
     */
    public void setLevel(Level newLevel) throws SecurityException {
        LogContext.checkAccess(loggerNode.getContext());
        loggerNode.setLevel(newLevel);
    }

    /**
     * Set the log level by name.  Uses the parent logging context's name registry; otherwise behaves
     * identically to {@link #setLevel(Level)}.
     *
     * @param newLevelName the name of the level to set
     * @throws SecurityException if a security manager exists and if the caller does not have LoggingPermission("control")
     */
    public void setLevelName(String newLevelName) throws SecurityException {
        setLevel(loggerNode.getContext().getLevelForName(newLevelName));
    }

    /**
     * Get the effective numerical log level, inherited from the parent.
     *
     * @return the effective level
     */
    public int getEffectiveLevel() {
        return loggerNode.getEffectiveLevel();
    }

    /** {@inheritDoc} */
    public Level getLevel() {
        return loggerNode.getLevel();
    }

    /** {@inheritDoc} */
    public boolean isLoggable(Level level) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        return level.intValue() >= effectiveLevel && effectiveLevel != OFF_INT;
    }

    // Attachment mgmt

    /**
     * Get the attachment value for a given key, or {@code null} if there is no such attachment.
     *
     * @param key the key
     * @param <V> the attachment value type
     * @return the attachment, or {@code null} if there is none for this key
     */
    @SuppressWarnings({ "unchecked" })
    public <V> V getAttachment(AttachmentKey<V> key) {
        return loggerNode.getAttachment(key);
    }

    /**
     * Attach an object to this logger under a given key.
     * A strong reference is maintained to the key and value for as long as this logger exists.
     *
     * @param key the attachment key
     * @param value the attachment value
     * @param <V> the attachment value type
     * @return the old attachment, if there was one
     * @throws SecurityException if a security manager exists and if the caller does not have {@code LoggingPermission(control)}
     */
    public <V> V attach(AttachmentKey<V> key, V value) throws SecurityException {
        LogContext.checkSecurityAccess();
        return loggerNode.attach(key, value);
    }

    /**
     * Attach an object to this logger under a given key, if such an attachment does not already exist.
     * A strong reference is maintained to the key and value for as long as this logger exists.
     *
     * @param key the attachment key
     * @param value the attachment value
     * @param <V> the attachment value type
     * @return the current attachment, if there is one, or {@code null} if the value was successfully attached
     * @throws SecurityException if a security manager exists and if the caller does not have {@code LoggingPermission(control)}
     */
    @SuppressWarnings({ "unchecked" })
    public <V> V attachIfAbsent(AttachmentKey<V> key, V value) throws SecurityException {
        LogContext.checkSecurityAccess();
        return loggerNode.attachIfAbsent(key, value);
    }

    /**
     * Remove an attachment.
     *
     * @param key the attachment key
     * @param <V> the attachment value type
     * @return the old value, or {@code null} if there was none
     * @throws SecurityException if a security manager exists and if the caller does not have {@code LoggingPermission(control)}
     */
    @SuppressWarnings({ "unchecked" })
    public <V> V detach(AttachmentKey<V> key) throws SecurityException {
        LogContext.checkAccess(loggerNode.getContext());
        return loggerNode.detach(key);
    }

    // Handler mgmt

    /** {@inheritDoc} */
    public void addHandler(Handler handler) throws SecurityException {
        LogContext.checkAccess(loggerNode.getContext());
        if (handler == null) {
            throw new NullPointerException("handler is null");
        }
        loggerNode.addHandler(handler);
    }

    /** {@inheritDoc} */
    public void removeHandler(Handler handler) throws SecurityException {
        LogContext.checkAccess(loggerNode.getContext());
        if (handler == null) {
            return;
        }
        loggerNode.removeHandler(handler);
    }

    /** {@inheritDoc} */
    public Handler[] getHandlers() {
        final Handler[] handlers = loggerNode.getHandlers();
        return handlers.length > 0 ? handlers.clone() : handlers;
    }

    /**
     * A convenience method to atomically replace the handler list for this logger.
     *
     * @param handlers the new handlers
     * @throws SecurityException if a security manager exists and if the caller does not have {@code LoggingPermission(control)}
     */
    public void setHandlers(final Handler[] handlers) throws SecurityException {
        LogContext.checkAccess(loggerNode.getContext());
        final Handler[] safeHandlers = handlers.clone();
        for (Handler handler : safeHandlers) {
            if (handler == null) {
                throw new IllegalArgumentException("A handler is null");
            }
        }
        loggerNode.setHandlers(safeHandlers);
    }

    /**
     * Atomically get and set the handler list for this logger.
     *
     * @param handlers the new handler set
     * @return the old handler set
     * @throws SecurityException if a security manager exists and if the caller does not have {@code LoggingPermission(control)}
     */
    public Handler[] getAndSetHandlers(final Handler[] handlers) throws SecurityException {
        LogContext.checkAccess(loggerNode.getContext());
        final Handler[] safeHandlers = handlers.clone();
        for (Handler handler : safeHandlers) {
            if (handler == null) {
                throw new IllegalArgumentException("A handler is null");
            }
        }
        return loggerNode.setHandlers(safeHandlers);
    }

    /**
     * Atomically compare and set the handler list for this logger.
     *
     * @param expected the expected list of handlers
     * @param newHandlers the replacement list of handlers
     * @return {@code true} if the handler list was updated or {@code false} if the current handlers did not match the expected handlers list
     * @throws SecurityException if a security manager exists and if the caller does not have {@code LoggingPermission(control)}
     */
    public boolean compareAndSetHandlers(final Handler[] expected, final Handler[] newHandlers) throws SecurityException {
        LogContext.checkAccess(loggerNode.getContext());
        final Handler[] safeExpectedHandlers = expected.clone();
        final Handler[] safeNewHandlers = newHandlers.clone();
        for (Handler handler : safeNewHandlers) {
            if (handler == null) {
                throw new IllegalArgumentException("A handler is null");
            }
        }
        Handler[] oldHandlers;
        do {
            oldHandlers = loggerNode.getHandlers();
            if (! Arrays.equals(oldHandlers, safeExpectedHandlers)) {
                return false;
            }
        } while (! loggerNode.compareAndSetHandlers(oldHandlers, safeNewHandlers));
        return true;
    }

    /**
     * A convenience method to atomically get and clear all handlers.
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code LoggingPermission(control)}
     */
    public Handler[] clearHandlers() throws SecurityException {
        LogContext.checkAccess(loggerNode.getContext());
        return loggerNode.clearHandlers();
    }

    /** {@inheritDoc} */
    public void setUseParentHandlers(boolean useParentHandlers) {
        LogContext.checkAccess(loggerNode.getContext());
        loggerNode.setUseParentHandlers(useParentHandlers);
    }

    /** {@inheritDoc} */
    public boolean getUseParentHandlers() {
        return loggerNode.getUseParentHandlers();
    }

    // Parent/child

    /** {@inheritDoc} */
    public Logger getParent() {
        final LoggerNode parentNode = loggerNode.getParent();
        return parentNode == null ? null : parentNode.createLogger();
    }

    /**
     * <b>Not allowed.</b>  This method may never be called.
     * @throws SecurityException always
     */
    public void setParent(java.util.logging.Logger parent) {
        throw new SecurityException("setParent() disallowed");
    }

    /**
     * Get the log context to which this logger belongs.
     *
     * @return the log context
     */
    public LogContext getLogContext() {
        return loggerNode.getContext();
    }

    // Logger

    static final int OFF_INT = Level.OFF.intValue();

    static final int SEVERE_INT = Level.SEVERE.intValue();
    static final int WARNING_INT = Level.WARNING.intValue();
    static final int INFO_INT = Level.INFO.intValue();
    static final int CONFIG_INT = Level.CONFIG.intValue();
    static final int FINE_INT = Level.FINE.intValue();
    static final int FINER_INT = Level.FINER.intValue();
    static final int FINEST_INT = Level.FINEST.intValue();

    /** {@inheritDoc} */
    public void log(LogRecord record) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (record.getLevel().intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        logRaw(record);
    }

    /** {@inheritDoc} */
    public void entering(final String sourceClass, final String sourceMethod) {
        if (FINER_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "ENTRY", LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void entering(final String sourceClass, final String sourceMethod, final Object param1) {
        if (FINER_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "ENTRY {0}", LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setParameters(new Object[] { param1 });
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void entering(final String sourceClass, final String sourceMethod, final Object[] params) {
        if (FINER_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        final StringBuilder builder = new StringBuilder("ENTRY");
        if (params != null) for (int i = 0; i < params.length; i++) {
            builder.append(" {").append(i).append('}');
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, builder.toString(), LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        if (params != null) rec.setParameters(params);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void exiting(final String sourceClass, final String sourceMethod) {
        if (FINER_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "RETURN", LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void exiting(final String sourceClass, final String sourceMethod, final Object result) {
        if (FINER_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "RETURN {0}", LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setParameters(new Object[] { result });
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void throwing(final String sourceClass, final String sourceMethod, final Throwable thrown) {
        if (FINER_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "THROW", LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setThrown(thrown);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void severe(final String msg) {
        if (SEVERE_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        logRaw(new ExtLogRecord(Level.SEVERE, msg, LOGGER_CLASS_NAME));
    }

    /** {@inheritDoc} */
    public void warning(final String msg) {
        if (WARNING_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        logRaw(new ExtLogRecord(Level.WARNING, msg, LOGGER_CLASS_NAME));
    }

    /** {@inheritDoc} */
    public void info(final String msg) {
        if (INFO_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        logRaw(new ExtLogRecord(Level.INFO, msg, LOGGER_CLASS_NAME));
    }

    /** {@inheritDoc} */
    public void config(final String msg) {
        if (CONFIG_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        logRaw(new ExtLogRecord(Level.CONFIG, msg, LOGGER_CLASS_NAME));
    }

    /** {@inheritDoc} */
    public void fine(final String msg) {
        if (FINE_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        logRaw(new ExtLogRecord(Level.FINE, msg, LOGGER_CLASS_NAME));
    }

    /** {@inheritDoc} */
    public void finer(final String msg) {
        if (FINER_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        logRaw(new ExtLogRecord(Level.FINER, msg, LOGGER_CLASS_NAME));
    }

    /** {@inheritDoc} */
    public void finest(final String msg) {
        if (FINEST_INT < loggerNode.getEffectiveLevel()) {
            return;
        }
        logRaw(new ExtLogRecord(Level.FINEST, msg, LOGGER_CLASS_NAME));
    }

    /** {@inheritDoc} */
    public void log(final Level level, final String msg) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        logRaw(new ExtLogRecord(level, msg, LOGGER_CLASS_NAME));
    }

    /** {@inheritDoc} */
    public void log(final Level level, final String msg, final Object param1) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { param1 });
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void log(final Level level, final String msg, final Object[] params) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        if (params != null) rec.setParameters(params);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void log(final Level level, final String msg, final Throwable thrown) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setThrown(thrown);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg, final Object param1) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setParameters(new Object[] { param1 });
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg, final Object[] params) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        if (params != null) rec.setParameters(params);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg, final Throwable thrown) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setThrown(thrown);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        super.logrb(level, sourceClass, sourceMethod, bundleName, msg);
    }

    /** {@inheritDoc} */
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg, final Object param1) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        super.logrb(level, sourceClass, sourceMethod, bundleName, msg, param1);
    }

    /** {@inheritDoc} */
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg, final Object[] params) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        super.logrb(level, sourceClass, sourceMethod, bundleName, msg, params);
    }

    /** {@inheritDoc} */
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg, final Throwable thrown) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        super.logrb(level, sourceClass, sourceMethod, bundleName, msg, thrown);
    }

    // alternate SPI hooks

    /**
     * SPI interface method to log a message at a given level, with a specific resource bundle.
     *
     * @param fqcn the fully qualified class name of the first logger class
     * @param level the level to log at
     * @param message the message
     * @param bundleName the resource bundle name
     * @param style the message format style
     * @param params the log parameters
     * @param t the throwable, if any
     */
    public void log(final String fqcn, final Level level, final String message, final String bundleName, final ExtLogRecord.FormatStyle style, final Object[] params, final Throwable t) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level == null || fqcn == null || message == null || level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, message, style, fqcn);
        rec.setResourceBundleName(bundleName);
        rec.setParameters(params);
        rec.setThrown(t);
        logRaw(rec);
    }

    /**
     * SPI interface method to log a message at a given level.
     *
     * @param fqcn the fully qualified class name of the first logger class
     * @param level the level to log at
     * @param message the message
     * @param style the message format style
     * @param params the log parameters
     * @param t the throwable, if any
     */
    public void log(final String fqcn, final Level level, final String message, final ExtLogRecord.FormatStyle style, final Object[] params, final Throwable t) {
        final int effectiveLevel = loggerNode.getEffectiveLevel();
        if (level == null || fqcn == null || message == null || level.intValue() < effectiveLevel || effectiveLevel == OFF_INT) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, message, style, fqcn);
        rec.setParameters(params);
        rec.setThrown(t);
        logRaw(rec);
    }

    /**
     * SPI interface method to log a message at a given level.
     *
     * @param fqcn the fully qualified class name of the first logger class
     * @param level the level to log at
     * @param message the message
     * @param t the throwable, if any
     */
    public void log(final String fqcn, final Level level, final String message, final Throwable t) {
        log(fqcn, level, message, ExtLogRecord.FormatStyle.MESSAGE_FORMAT, null, t);
    }

    /**
     * Do the logging with no level checks (they've already been done).
     *
     * @param record the extended log record
     */
    public void logRaw(final ExtLogRecord record) {
        record.setLoggerName(getName());
        String bundleName = null;
        ResourceBundle bundle = null;
        // todo: new parents never have resource bundles; this could cause an issue
        bundleName = getResourceBundleName();
        bundle = getResourceBundle();
//        for (Logger current = this; current != null; current = current.getParent()) {
//            bundleName = current.getResourceBundleName();
//            if (bundleName != null) {
//                bundle = current.getResourceBundle();
//                break;
//            }
//        }
        if (bundleName != null && bundle != null) {
            record.setResourceBundleName(bundleName);
            record.setResourceBundle(bundle);
        }
        final Filter filter = loggerNode.getFilter();
        try {
            if (filter != null && ! filter.isLoggable(record)) {
                return;
            }
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            // todo - error handler
            // treat an errored filter as "pass" (I guess?)
        }
        loggerNode.publish(record);
    }

    /**
     * Do the logging with no level checks (they've already been done).  Creates an extended log record if the
     * provided record is not one.
     *
     * @param record the log record
     */
    public void logRaw(final LogRecord record) {
        logRaw(ExtLogRecord.wrap(record));
    }

    /**
     * An attachment key instance.
     *
     * @param <V> the attachment value type
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public static final class AttachmentKey<V> {

        /**
         * Construct a new instance.
         */
        public AttachmentKey() {
        }
    }

    public String toString() {
        return "Logger '" + getName() + "' in context " + loggerNode.getContext();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            loggerNode.decrementRef();
        } finally {
            super.finalize();
        }
    }
}
