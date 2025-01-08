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

import java.io.Flushable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Permission;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.LoggingPermission;

import org.jboss.logmanager.errormanager.OnlyOnceErrorManager;

/**
 * An extended logger handler. Use this class as a base class for log handlers which require {@code ExtLogRecord}
 * instances.
 */
public abstract class ExtHandler extends Handler implements AutoCloseable, Flushable {

    private static final ErrorManager DEFAULT_ERROR_MANAGER = new OnlyOnceErrorManager();
    private static final Permission CONTROL_PERMISSION = new LoggingPermission("control", null);

    protected final ReentrantLock lock = new ReentrantLock();

    // we keep our own copies of these fields so that they are protected with *our* lock:
    private volatile Filter filter;
    private volatile Formatter formatter;
    private volatile Level level = Level.ALL;
    private volatile ErrorManager errorManager;
    // (skip `encoding` because we replace it with `charset` below)

    private volatile boolean autoFlush = true;
    private volatile boolean enabled = true;
    private volatile boolean closeChildren;
    private volatile Charset charset = StandardCharsets.UTF_8;

    /**
     * The sub-handlers for this handler. May only be updated using the {@link #handlersUpdater} atomic updater. The array
     * instance should not be modified (treat as immutable).
     */
    @SuppressWarnings("unused")
    protected volatile Handler[] handlers;

    /**
     * The atomic updater for the {@link #handlers} field.
     */
    protected static final AtomicArray<ExtHandler, Handler> handlersUpdater = AtomicArray
            .create(AtomicReferenceFieldUpdater.newUpdater(ExtHandler.class, Handler[].class, "handlers"), Handler.class);

    /**
     * Construct a new instance.
     */
    protected ExtHandler() {
        handlersUpdater.clear(this);
        closeChildren = true;
        errorManager = DEFAULT_ERROR_MANAGER;
    }

    /** {@inheritDoc} */
    public void publish(final LogRecord record) {
        if (enabled && record != null && isLoggable(record)) {
            doPublish(ExtLogRecord.wrap(record));
        }
    }

    /**
     * Publish an {@code ExtLogRecord}.
     * <p/>
     * The logging request was made initially to a Logger object, which initialized the LogRecord and forwarded it here.
     * <p/>
     * The {@code ExtHandler} is responsible for formatting the message, when and if necessary. The formatting should
     * include localization.
     *
     * @param record the log record to publish
     */
    public void publish(final ExtLogRecord record) {
        if (enabled && record != null && isLoggable(record))
            try {
                doPublish(record);
            } catch (Exception e) {
                reportError("Handler publication threw an exception", e, ErrorManager.WRITE_FAILURE);
            } catch (Throwable ignored) {
            }
    }

    /**
     * Publish a log record to each nested handler.
     *
     * @param record the log record to publish
     */
    @SuppressWarnings("deprecation") // record.getFormattedMessage()
    protected void publishToNestedHandlers(final ExtLogRecord record) {
        if (record != null) {
            ExtLogRecord oldRecord = null;
            for (Handler handler : getHandlers())
                try {
                    if (handler != null) {
                        if (handler instanceof ExtHandler || handler.getFormatter() instanceof ExtFormatter) {
                            handler.publish(record);
                        } else {
                            // old-style handlers generally don't know how to handle printf formatting
                            if (oldRecord == null) {
                                if (record.getFormatStyle() == ExtLogRecord.FormatStyle.PRINTF) {
                                    // reformat it in a simple way, but only for legacy handler usage
                                    oldRecord = new ExtLogRecord(record);
                                    oldRecord.setMessage(record.getFormattedMessage(), ExtLogRecord.FormatStyle.NO_FORMAT);
                                    oldRecord.setParameters(null);
                                } else {
                                    oldRecord = record;
                                }
                            }
                            handler.publish(oldRecord);
                        }
                    }
                } catch (Exception e) {
                    reportError(handler, "Nested handler publication threw an exception", e, ErrorManager.WRITE_FAILURE);
                } catch (Throwable ignored) {
                }
        }
    }

    /**
     * Do the actual work of publication; the record will have been filtered already. The default implementation
     * does nothing except to flush if the {@code autoFlush} property is set to {@code true}; if this behavior is to be
     * preserved in a subclass then this method should be called after the record is physically written.
     *
     * @param record the log record to publish
     */
    protected void doPublish(final ExtLogRecord record) {
        if (autoFlush)
            flush();
    }

    /**
     * Add a sub-handler to this handler. Some handler types do not utilize sub-handlers.
     *
     * @param handler the handler to add
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public void addHandler(Handler handler) throws SecurityException {
        checkAccess();
        if (handler == null) {
            throw new NullPointerException("handler is null");
        }
        handlersUpdater.add(this, handler);
    }

    /**
     * Remove a sub-handler from this handler. Some handler types do not utilize sub-handlers.
     *
     * @param handler the handler to remove
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public void removeHandler(Handler handler) throws SecurityException {
        checkAccess();
        if (handler == null) {
            return;
        }
        handlersUpdater.remove(this, handler, true);
    }

    /**
     * Get a copy of the sub-handlers array. Since the returned value is a copy, it may be freely modified.
     *
     * @return a copy of the sub-handlers array
     */
    public Handler[] getHandlers() {
        final Handler[] handlers = this.handlers;
        return handlers.length > 0 ? handlers.clone() : handlers;
    }

    /**
     * A convenience method to atomically get and clear all sub-handlers.
     *
     * @return the old sub-handler array
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public Handler[] clearHandlers() throws SecurityException {
        checkAccess();
        final Handler[] handlers = this.handlers;
        handlersUpdater.clear(this);
        return handlers.length > 0 ? handlers.clone() : handlers;
    }

    /**
     * A convenience method to atomically get and replace the sub-handler array.
     *
     * @param newHandlers the new sub-handlers
     * @return the old sub-handler array
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public Handler[] setHandlers(final Handler[] newHandlers) throws SecurityException {
        if (newHandlers == null) {
            throw new IllegalArgumentException("newHandlers is null");
        }
        if (newHandlers.length == 0) {
            return clearHandlers();
        } else {
            checkAccess();
            final Handler[] handlers = handlersUpdater.getAndSet(this, newHandlers);
            return handlers.length > 0 ? handlers.clone() : handlers;
        }
    }

    /**
     * Determine if this handler will auto-flush.
     *
     * @return {@code true} if auto-flush is enabled
     */
    public boolean isAutoFlush() {
        return autoFlush;
    }

    /**
     * Change the autoflush setting for this handler.
     *
     * @param autoFlush {@code true} to automatically flush after each write; {@code false} otherwise
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public void setAutoFlush(final boolean autoFlush) throws SecurityException {
        checkAccess();
        this.autoFlush = autoFlush;
        if (autoFlush) {
            flush();
        }
    }

    /**
     * Enables or disables the handler based on the value passed in.
     *
     * @param enabled {@code true} to enable the handler or {@code false} to disable the handler.
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    public final void setEnabled(final boolean enabled) throws SecurityException {
        checkAccess();
        this.enabled = enabled;
    }

    /**
     * Determine if the handler is enabled.
     *
     * @return {@code true} if the handler is enabled, otherwise {@code false}.
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Indicates whether or not children handlers should be closed when this handler is {@linkplain #close() closed}.
     *
     * @return {@code true} if the children handlers should be closed when this handler is closed, {@code false} if
     *         children handlers should not be closed when this handler is closed
     */
    public boolean isCloseChildren() {
        return closeChildren;
    }

    /**
     * Sets whether or not children handlers should be closed when this handler is {@linkplain #close() closed}.
     *
     * @param closeChildren {@code true} if all children handlers should be closed when this handler is closed,
     *                      {@code false} if children handlers will <em>not</em> be closed when this handler
     *                      is closed
     */
    public void setCloseChildren(final boolean closeChildren) {
        checkAccess();
        this.closeChildren = closeChildren;
    }

    /**
     * Check access.
     *
     * @throws SecurityException if a security manager is installed and the caller does not have the
     *                           {@code "control" LoggingPermission}
     */
    protected static void checkAccess() throws SecurityException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONTROL_PERMISSION);
        }
    }

    /**
     * Check access.
     *
     * @param handler the handler to check access on.
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)}
     */
    @Deprecated
    protected static void checkAccess(final ExtHandler handler) throws SecurityException {
        checkAccess();
    }

    /**
     * Flush all child handlers.
     */
    @Override
    public void flush() {
        for (Handler handler : handlers)
            try {
                handler.flush();
            } catch (Exception ex) {
                reportError("Failed to flush child handler", ex, ErrorManager.FLUSH_FAILURE);
            } catch (Throwable ignored) {
            }
    }

    /**
     * Close all child handlers.
     */
    @Override
    public void close() throws SecurityException {
        checkAccess();
        if (closeChildren) {
            for (Handler handler : handlers)
                try {
                    handler.close();
                } catch (Exception ex) {
                    reportError("Failed to close child handler", ex, ErrorManager.CLOSE_FAILURE);
                } catch (Throwable ignored) {
                }
        }
    }

    @Override
    public void setFormatter(final Formatter newFormatter) throws SecurityException {
        checkAccess();
        Objects.requireNonNull(newFormatter);
        lock.lock();
        try {
            formatter = newFormatter;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Formatter getFormatter() {
        return formatter;
    }

    @Override
    public void setFilter(final Filter newFilter) throws SecurityException {
        checkAccess();
        lock.lock();
        try {
            filter = newFilter;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

    /**
     * Set the handler's character set by name. This is roughly equivalent to calling {@link #setCharset(Charset)} with
     * the results of {@link Charset#forName(String)}.
     *
     * @param encoding the name of the encoding
     * @throws SecurityException            if a security manager is installed and the caller does not have the
     *                                      {@code "control" LoggingPermission}
     * @throws UnsupportedEncodingException if no character set could be found for the encoding name
     */
    @Override
    public void setEncoding(final String encoding) throws SecurityException, UnsupportedEncodingException {
        if (encoding != null) {
            try {
                setCharset(Charset.forName(encoding));
            } catch (IllegalArgumentException e) {
                final UnsupportedEncodingException e2 = new UnsupportedEncodingException(
                        "Unable to set encoding to \"" + encoding + "\"");
                e2.initCause(e);
                throw e2;
            }
        } else {
            setCharset(StandardCharsets.UTF_8);
        }
    }

    /**
     * Get the name of the {@linkplain #getCharset() handler's character set}.
     *
     * @return the handler character set name
     */
    @Override
    public String getEncoding() {
        return getCharset().name();
    }

    /**
     * Set the handler's character set. If not set, the handler's character set is initialized to the platform default
     * character set.
     *
     * @param charset the character set (must not be {@code null})
     * @throws SecurityException if a security manager is installed and the caller does not have the
     *                           {@code "control" LoggingPermission}
     */
    public void setCharset(final Charset charset) throws SecurityException {
        checkAccess();
        setCharsetPrivate(charset);
    }

    /**
     * Set the handler's character set from within this handler. If not set, the handler's character set is initialized
     * to the platform default character set.
     *
     * @param charset the character set (must not be {@code null})
     */
    protected void setCharsetPrivate(final Charset charset) throws SecurityException {
        Objects.requireNonNull(charset, "charset");
        lock.lock();
        try {
            this.charset = charset;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the handler's character set.
     *
     * @return the character set in use (not {@code null})
     */
    public Charset getCharset() {
        return charset;
    }

    @Override
    public void setErrorManager(final ErrorManager em) {
        Objects.requireNonNull(em);
        checkAccess();
        lock.lock();
        try {
            errorManager = em;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ErrorManager getErrorManager() {
        return errorManager;
    }

    @Override
    public void setLevel(final Level newLevel) throws SecurityException {
        Objects.requireNonNull(newLevel);
        checkAccess();
        lock.lock();
        try {
            level = newLevel;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Level getLevel() {
        return level;
    }

    /**
     * Indicates whether or not the {@linkplain #getFormatter() formatter} associated with this handler or a formatter
     * from a {@linkplain #getHandlers() child handler} requires the caller to be calculated.
     * <p>
     * Calculating the caller on a {@linkplain ExtLogRecord log record} can be an expensive operation. Some handlers
     * may be required to copy some data from the log record, but may not need the caller information. If the
     * {@linkplain #getFormatter() formatter} is a {@link ExtFormatter} the
     * {@link ExtFormatter#isCallerCalculationRequired()} is used to determine if calculation of the caller is
     * required.
     * </p>
     *
     * @return {@code true} if the caller should be calculated, otherwise {@code false} if it can be skipped
     *
     * @see LogRecord#getSourceClassName()
     * @see ExtLogRecord#getSourceFileName()
     * @see ExtLogRecord#getSourceLineNumber()
     * @see LogRecord#getSourceMethodName()
     */
    @SuppressWarnings("WeakerAccess")
    public boolean isCallerCalculationRequired() {
        Formatter formatter = getFormatter();
        if (formatterRequiresCallerCalculation(formatter)) {
            return true;
        } else
            for (Handler handler : getHandlers()) {
                if (handler instanceof ExtHandler) {
                    if (((ExtHandler) handler).isCallerCalculationRequired()) {
                        return true;
                    }
                } else {
                    formatter = handler.getFormatter();
                    if (formatterRequiresCallerCalculation(formatter)) {
                        return true;
                    }
                }
            }
        return false;
    }

    @Override
    protected void reportError(String msg, Exception ex, int code) {
        final ErrorManager errorManager = this.errorManager;
        errorManager.error(msg, ex, code);
    }

    /**
     * Report an error using a handler's specific error manager, if any.
     *
     * @param handler the handler
     * @param msg     the error message
     * @param ex      the exception
     * @param code    the error code
     */
    public static void reportError(Handler handler, String msg, Exception ex, int code) {
        if (handler != null) {
            ErrorManager errorManager = handler.getErrorManager();
            if (errorManager != null)
                try {
                    errorManager.error(msg, ex, code);
                } catch (Exception ex2) {
                    // use the same message as the JDK
                    System.err.println("Handler.reportError caught:");
                    ex2.printStackTrace();
                }
        }
    }

    private static boolean formatterRequiresCallerCalculation(final Formatter formatter) {
        return formatter != null
                && (!(formatter instanceof ExtFormatter) || ((ExtFormatter) formatter).isCallerCalculationRequired());
    }
}
