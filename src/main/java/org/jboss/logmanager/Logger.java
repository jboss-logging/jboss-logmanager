/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager;

import static org.jboss.logmanager.ExtLogRecord.FormatStyle.MESSAGE_FORMAT;
import static org.jboss.logmanager.ExtLogRecord.FormatStyle.NO_FORMAT;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Supplier;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * An actual logger instance. This is the end-user interface into the logging system.
 */
@SuppressWarnings({ "SerializableClassWithUnconstructableAncestor" })
public final class Logger extends java.util.logging.Logger implements Serializable {

    private static final long serialVersionUID = 5093333069125075416L;
    private static final ResourceBundle TOMBSTONE = new ResourceBundle() {
        @Override
        protected Object handleGetObject(final String key) {
            return null;
        }

        @Override
        public Enumeration<String> getKeys() {
            return null;
        }
    };

    /**
     * The named logger tree node.
     */
    private final LoggerNode loggerNode;

    /**
     * The resource bundle for this logger.
     */
    private volatile ResourceBundle resourceBundle;

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
     * Static logger factory method which returns a JBoss LogManager logger.
     *
     * @param name       the logger name
     * @param bundleName the bundle name
     * @return the logger
     */
    public static Logger getLogger(final String name, final String bundleName) {
        final Logger logger = LogContext.getLogContext().getLogger(name);
        logger.resourceBundle = ResourceBundle.getBundle(bundleName, Locale.getDefault(), Logger.class.getClassLoader());
        return logger;
    }

    /**
     * Construct a new instance of an actual logger.
     *
     * @param loggerNode the node in the named logger tree
     * @param name       the fully-qualified name of this node
     */
    Logger(final LoggerNode loggerNode, final String name) {
        // Don't set up the bundle in the parent...
        super(name, null);
        // We have to propagate our level to an internal data structure in the superclass
        super.setLevel(loggerNode.getLevel());
        this.loggerNode = loggerNode;
    }
    // Serialization

    protected final Object writeReplace() throws ObjectStreamException {
        return new SerializedLogger(getName());
    }

    // Filter mgmt

    /** {@inheritDoc} */
    public void setFilter(Filter filter) throws SecurityException {
        LogContext.checkAccess();
        loggerNode.setFilter(filter);
    }

    /** {@inheritDoc} */
    public Filter getFilter() {
        return loggerNode.getFilter();
    }

    // Level mgmt

    /**
     * {@inheritDoc} This implementation grabs a lock, so that only one thread may update the log level of any
     * logger at a time, in order to allow readers to never block (though there is a window where retrieving the
     * log level reflects an older effective level than the actual level).
     */
    public void setLevel(Level newLevel) throws SecurityException {
        LogContext.checkAccess();
        // We have to propagate our level to an internal data structure in the superclass
        super.setLevel(newLevel);
        loggerNode.setLevel(newLevel);
    }

    /**
     * Set the log level by name. Uses the parent logging context's name registry; otherwise behaves
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
        return loggerNode.isLoggableLevel(level.intValue());
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
     * @param key   the attachment key
     * @param value the attachment value
     * @param <V>   the attachment value type
     * @return the old attachment, if there was one
     * @throws SecurityException        if a security manager exists and if the caller does not have
     *                                  {@code LoggingPermission(control)}
     * @throws IllegalArgumentException if the attachment cannot be added because the maximum has been reached
     */
    public <V> V attach(AttachmentKey<V> key, V value) throws SecurityException {
        LogContext.checkAccess();
        return loggerNode.attach(key, value);
    }

    /**
     * Attach an object to this logger under a given key, if such an attachment does not already exist.
     * A strong reference is maintained to the key and value for as long as this logger exists.
     *
     * @param key   the attachment key
     * @param value the attachment value
     * @param <V>   the attachment value type
     * @return the current attachment, if there is one, or {@code null} if the value was successfully attached
     * @throws SecurityException        if a security manager exists and if the caller does not have
     *                                  {@code LoggingPermission(control)}
     * @throws IllegalArgumentException if the attachment cannot be added because the maximum has been reached
     */
    @SuppressWarnings({ "unchecked" })
    public <V> V attachIfAbsent(AttachmentKey<V> key, V value) throws SecurityException {
        LogContext.checkAccess();
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
        LogContext.checkAccess();
        return loggerNode.detach(key);
    }

    // Handler mgmt

    /** {@inheritDoc} */
    public void addHandler(Handler handler) throws SecurityException {
        LogContext.checkAccess();
        if (handler == null) {
            throw new NullPointerException("handler is null");
        }
        loggerNode.addHandler(handler);
    }

    /** {@inheritDoc} */
    public void removeHandler(Handler handler) throws SecurityException {
        LogContext.checkAccess();
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
        LogContext.checkAccess();
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
        LogContext.checkAccess();
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
     * @param expected    the expected list of handlers
     * @param newHandlers the replacement list of handlers
     * @return {@code true} if the handler list was updated or {@code false} if the current handlers did not match the expected
     *         handlers list
     * @throws SecurityException if a security manager exists and if the caller does not have {@code LoggingPermission(control)}
     */
    public boolean compareAndSetHandlers(final Handler[] expected, final Handler[] newHandlers) throws SecurityException {
        LogContext.checkAccess();
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
            if (!Arrays.equals(oldHandlers, safeExpectedHandlers)) {
                return false;
            }
        } while (!loggerNode.compareAndSetHandlers(oldHandlers, safeNewHandlers));
        return true;
    }

    /**
     * A convenience method to atomically get and clear all handlers.
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code LoggingPermission(control)}
     */
    public Handler[] clearHandlers() throws SecurityException {
        LogContext.checkAccess();
        return loggerNode.clearHandlers();
    }

    /** {@inheritDoc} */
    public void setUseParentHandlers(boolean useParentHandlers) {
        LogContext.checkAccess();
        loggerNode.setUseParentHandlers(useParentHandlers);
    }

    /** {@inheritDoc} */
    public boolean getUseParentHandlers() {
        return loggerNode.getUseParentHandlers();
    }

    /**
     * Specify whether or not filters should be inherited from parent loggers.
     * <p>
     * Setting this value to {@code false} has the same behaviour as {@linkplain java.util.logging.Logger}.
     * </p>
     *
     * @param useParentFilter {@code true} to inherit a parents filter, otherwise {@code false}
     */
    public void setUseParentFilters(final boolean useParentFilter) {
        LogContext.checkAccess();
        loggerNode.setUseParentFilters(useParentFilter);
    }

    /**
     * Indicates whether or not this logger inherits filters from it's parent logger.
     *
     * @return {@code true} if filters are inherited, otherwise {@code false}
     */
    public boolean getUseParentFilters() {
        return loggerNode.getUseParentFilters();
    }

    // Parent/child

    /** {@inheritDoc} */
    public Logger getParent() {
        final LoggerNode parentNode = loggerNode.getParent();
        return parentNode == null ? null : parentNode.createLogger();
    }

    /**
     * This method should never be called and any calls to it will be ignored. Some libraries attempt to call this,
     * so we have to implement as noop so they don't fail horribly.
     */
    public void setParent(java.util.logging.Logger parent) {
        // No-op.
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
        if (!loggerNode.isLoggableLevel(record.getLevel().intValue())) {
            return;
        }
        logRaw(record);
    }

    /** {@inheritDoc} */
    public void entering(final String sourceClass, final String sourceMethod) {
        if (!loggerNode.isLoggableLevel(FINER_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "ENTRY", NO_FORMAT, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void entering(final String sourceClass, final String sourceMethod, final Object param1) {
        if (!loggerNode.isLoggableLevel(FINER_INT)) {
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
        if (!loggerNode.isLoggableLevel(FINER_INT)) {
            return;
        }
        final StringBuilder builder = new StringBuilder("ENTRY");
        ExtLogRecord.FormatStyle style;
        if (params != null && params.length > 0) {
            for (int i = 0; i < params.length; i++) {
                builder.append(" {").append(i).append('}');
            }
            style = MESSAGE_FORMAT;
        } else {
            style = NO_FORMAT;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, builder.toString(), style, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        if (params != null)
            rec.setParameters(params);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void exiting(final String sourceClass, final String sourceMethod) {
        if (!loggerNode.isLoggableLevel(FINER_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "RETURN", NO_FORMAT, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void exiting(final String sourceClass, final String sourceMethod, final Object result) {
        if (!loggerNode.isLoggableLevel(FINER_INT)) {
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
        if (!loggerNode.isLoggableLevel(FINER_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, "THROW", NO_FORMAT, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setThrown(thrown);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void severe(final String msg) {
        if (!loggerNode.isLoggableLevel(SEVERE_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.SEVERE, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    @Override
    public void severe(final Supplier<String> msgSupplier) {
        if (!loggerNode.isLoggableLevel(SEVERE_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.SEVERE, msgSupplier.get(), NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void warning(final String msg) {
        if (!loggerNode.isLoggableLevel(WARNING_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.WARNING, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    @Override
    public void warning(final Supplier<String> msgSupplier) {
        if (!loggerNode.isLoggableLevel(WARNING_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.WARNING, msgSupplier.get(), NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void info(final String msg) {
        if (!loggerNode.isLoggableLevel(INFO_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.INFO, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    @Override
    public void info(final Supplier<String> msgSupplier) {
        if (!loggerNode.isLoggableLevel(INFO_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.INFO, msgSupplier.get(), NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void config(final String msg) {
        if (!loggerNode.isLoggableLevel(CONFIG_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.CONFIG, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    @Override
    public void config(final Supplier<String> msgSupplier) {
        if (!loggerNode.isLoggableLevel(CONFIG_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.CONFIG, msgSupplier.get(), NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void fine(final String msg) {
        if (!loggerNode.isLoggableLevel(FINE_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINE, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    @Override
    public void fine(final Supplier<String> msgSupplier) {
        if (!loggerNode.isLoggableLevel(FINE_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINE, msgSupplier.get(), NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void finer(final String msg) {
        if (!loggerNode.isLoggableLevel(FINER_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    @Override
    public void finer(final Supplier<String> msgSupplier) {
        if (!loggerNode.isLoggableLevel(FINER_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINER, msgSupplier.get(), NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void finest(final String msg) {
        if (!loggerNode.isLoggableLevel(FINEST_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINEST, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    @Override
    public void finest(final Supplier<String> msgSupplier) {
        if (!loggerNode.isLoggableLevel(FINEST_INT)) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(Level.FINEST, msgSupplier.get(), NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void log(final Level level, final String msg) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    @Override
    public void log(final Level level, final Supplier<String> msgSupplier) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msgSupplier.get(), NO_FORMAT, LOGGER_CLASS_NAME);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void log(final Level level, final String msg, final Object param1) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setParameters(new Object[] { param1 });
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void log(final Level level, final String msg, final Object[] params) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec;
        if (params != null && params.length > 0) {
            rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
            rec.setParameters(params);
        } else {
            rec = new ExtLogRecord(level, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        }
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void log(final Level level, final String msg, final Throwable thrown) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        rec.setThrown(thrown);
        logRaw(rec);
    }

    @Override
    public void log(final Level level, final Throwable thrown, final Supplier<String> msgSupplier) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msgSupplier.get(), NO_FORMAT, LOGGER_CLASS_NAME);
        rec.setThrown(thrown);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        logRaw(rec);
    }

    @Override
    public void logp(final Level level, final String sourceClass, final String sourceMethod,
            final Supplier<String> msgSupplier) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msgSupplier.get(), NO_FORMAT, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg,
            final Object param1) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setParameters(new Object[] { param1 });
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg,
            final Object[] params) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec;
        if (params != null && params.length > 0) {
            rec = new ExtLogRecord(level, msg, LOGGER_CLASS_NAME);
            rec.setParameters(params);
        } else {
            rec = new ExtLogRecord(level, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        }
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg,
            final Throwable thrown) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msg, NO_FORMAT, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setThrown(thrown);
        logRaw(rec);
    }

    @Override
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final Throwable thrown,
            final Supplier<String> msgSupplier) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        final ExtLogRecord rec = new ExtLogRecord(level, msgSupplier.get(), NO_FORMAT, LOGGER_CLASS_NAME);
        rec.setSourceClassName(sourceClass);
        rec.setSourceMethodName(sourceMethod);
        rec.setThrown(thrown);
        logRaw(rec);
    }

    /** {@inheritDoc} */
    @Deprecated(since = "3.0", forRemoval = true)
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName,
            final String msg) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        // No local check is needed here as this will delegate to log(LogRecord)
        super.logrb(level, sourceClass, sourceMethod, bundleName, msg);
    }

    /** {@inheritDoc} */
    @Deprecated(since = "3.0", forRemoval = true)
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName,
            final String msg, final Object param1) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        // No local check is needed here as this will delegate to log(LogRecord)
        super.logrb(level, sourceClass, sourceMethod, bundleName, msg, param1);
    }

    /** {@inheritDoc} */
    @Deprecated(since = "3.0", forRemoval = true)
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName,
            final String msg, final Object[] params) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        // No local check is needed here as this will delegate to log(LogRecord)
        super.logrb(level, sourceClass, sourceMethod, bundleName, msg, params);
    }

    /** {@inheritDoc} */
    @Deprecated(since = "3.0", forRemoval = true)
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName,
            final String msg, final Throwable thrown) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        // No local check is needed here as this will delegate to log(LogRecord)
        super.logrb(level, sourceClass, sourceMethod, bundleName, msg, thrown);
    }

    @Override
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final ResourceBundle bundle,
            final String msg, final Object... params) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        // No local check is needed here as this will delegate to log(LogRecord)
        super.logrb(level, sourceClass, sourceMethod, bundle, msg, params);
    }

    @Override
    public void logrb(final Level level, final ResourceBundle bundle, final String msg, final Object... params) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        // No local check is needed here as this will delegate to log(LogRecord)
        super.logrb(level, bundle, msg, params);
    }

    @Override
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final ResourceBundle bundle,
            final String msg, final Throwable thrown) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        // No local check is needed here as this will delegate to log(LogRecord)
        super.logrb(level, sourceClass, sourceMethod, bundle, msg, thrown);
    }

    @Override
    public void logrb(final Level level, final ResourceBundle bundle, final String msg, final Throwable thrown) {
        if (!loggerNode.isLoggableLevel(level.intValue())) {
            return;
        }
        // No local check is needed here as this will delegate to log(LogRecord)
        super.logrb(level, bundle, msg, thrown);
    }
    // alternate SPI hooks

    /**
     * SPI interface method to log a message at a given level, with a specific resource bundle.
     *
     * @param fqcn       the fully qualified class name of the first logger class
     * @param level      the level to log at
     * @param message    the message
     * @param bundleName the resource bundle name
     * @param style      the message format style
     * @param params     the log parameters
     * @param t          the throwable, if any
     */
    public void log(final String fqcn, final Level level, final String message, final String bundleName,
            final ExtLogRecord.FormatStyle style, final Object[] params, final Throwable t) {
        if (level == null || fqcn == null || message == null
                || !loggerNode.isLoggableLevel(level.intValue())) {
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
     * @param fqcn    the fully qualified class name of the first logger class
     * @param level   the level to log at
     * @param message the message
     * @param style   the message format style
     * @param params  the log parameters
     * @param t       the throwable, if any
     */
    public void log(final String fqcn, final Level level, final String message, final ExtLogRecord.FormatStyle style,
            final Object[] params, final Throwable t) {
        if (level == null || fqcn == null || message == null
                || !loggerNode.isLoggableLevel(level.intValue())) {
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
     * @param fqcn    the fully qualified class name of the first logger class
     * @param level   the level to log at
     * @param message the message
     * @param t       the throwable, if any
     */
    public void log(final String fqcn, final Level level, final String message, final Throwable t) {
        log(fqcn, level, message, NO_FORMAT, null, t);
    }

    /**
     * Do the logging with no level checks (they've already been done).
     *
     * @param record the extended log record
     */
    public void logRaw(final ExtLogRecord record) {
        record.setLoggerName(getName());
        final ResourceBundle bundle = getResourceBundle();
        if (bundle != null) {
            record.setResourceBundleName(bundle.getBaseBundleName());
            record.setResourceBundle(bundle);
        }
        try {
            if (!loggerNode.isLoggable(record)) {
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
     * Set the resource bundle for this logger.
     *
     * @param resourceBundle the resource bundle (must not be {@code null})
     */
    @Override
    public void setResourceBundle(ResourceBundle resourceBundle) {
        super.setResourceBundle(resourceBundle);
        synchronized (this) {
            this.resourceBundle = resourceBundle;
        }
    }

    /**
     * Get the resource bundle for this logger.
     *
     * @return the resource bundle, or {@code null} if none is configured for this logger
     */
    @Override
    public ResourceBundle getResourceBundle() {
        if (resourceBundle == null) {
            synchronized (this) {
                if (resourceBundle == null) {
                    resourceBundle = super.getResourceBundle();
                    if (resourceBundle == null) {
                        resourceBundle = TOMBSTONE;
                    }
                }
            }
        }
        return resourceBundle == TOMBSTONE ? null : resourceBundle;
    }

    /**
     * Do the logging with no level checks (they've already been done). Creates an extended log record if the
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
}
