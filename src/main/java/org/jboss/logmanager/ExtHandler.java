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

import java.io.UnsupportedEncodingException;

import java.security.Permission;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.LoggingPermission;

import org.jboss.logmanager.errormanager.OnlyOnceErrorManager;
import org.jboss.logmanager.handlers.FlushableCloseable;

/**
 * An extended logger handler.  Use this class as a base class for log handlers which require {@code ExtLogRecord}
 * instances.
 */
public abstract class ExtHandler extends Handler implements FlushableCloseable, Protectable {

    private static final Permission CONTROL_PERMISSION = new LoggingPermission("control", null);
    private volatile boolean autoFlush = true;
    private volatile boolean enabled = true;
    private static final ErrorManager DEFAULT_ERROR_MANAGER = new OnlyOnceErrorManager();

    private volatile Object protectKey;
    private final ThreadLocal<Boolean> granted = new InheritableThreadLocal<Boolean>();

    private static final AtomicReferenceFieldUpdater<ExtHandler, Object> protectKeyUpdater = AtomicReferenceFieldUpdater.newUpdater(ExtHandler.class, Object.class, "protectKey");

    /**
     * The sub-handlers for this handler.  May only be updated using the {@link #handlersUpdater} atomic updater.  The array
     * instance should not be modified (treat as immutable).
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    protected volatile Handler[] handlers;

    /**
     * The atomic updater for the {@link #handlers} field.
     */
    protected static final AtomicArray<ExtHandler, Handler> handlersUpdater = AtomicArray.create(AtomicReferenceFieldUpdater.newUpdater(ExtHandler.class, Handler[].class, "handlers"), Handler.class);

    /**
     * Construct a new instance.
     */
    protected ExtHandler() {
        handlersUpdater.clear(this);
        super.setErrorManager(DEFAULT_ERROR_MANAGER);
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
        if (enabled && record != null && isLoggable(record)) {
            doPublish(record);
        }
    }

    /**
     * Do the actual work of publication; the record will have been filtered already.  The default implementation
     * does nothing except to flush if the {@code autoFlush} property is set to {@code true}; if this behavior is to be
     * preserved in a subclass then this method should be called after the record is physically written.
     *
     * @param record the log record to publish
     */
    protected void doPublish(final ExtLogRecord record) {
        if (autoFlush) flush();
    }

    /**
     * Add a sub-handler to this handler.  Some handler types do not utilize sub-handlers.
     *
     * @param handler the handler to add
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}.
     */
    public void addHandler(Handler handler) throws SecurityException {
        checkAccess(this);
        if (handler == null) {
            throw new NullPointerException("handler is null");
        }
        handlersUpdater.add(this, handler);
    }

    /**
     * Remove a sub-handler from this handler.  Some handler types do not utilize sub-handlers.
     *
     * @param handler the handler to remove
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}.
     */
    public void removeHandler(Handler handler) throws SecurityException {
        checkAccess(this);
        if (handler == null) {
            return;
        }
        handlersUpdater.remove(this, handler, true);
    }

    /**
     * Get a copy of the sub-handlers array.  Since the returned value is a copy, it may be freely modified.
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
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}.
     */
    public Handler[] clearHandlers() throws SecurityException {
        checkAccess(this);
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
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}.
     */
    public Handler[] setHandlers(final Handler[] newHandlers) throws SecurityException {
        if (newHandlers == null) {
            throw new IllegalArgumentException("newHandlers is null");
        }
        if (newHandlers.length == 0) {
            return clearHandlers();
        } else {
            checkAccess(this);
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
     * @param autoFlush {@code true} to automatically flush after each write; false otherwise
     *
     * @throws SecurityException if a security manager exists and if the caller does not have {@code
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}.
     */
    public void setAutoFlush(final boolean autoFlush) throws SecurityException {
        checkAccess(this);
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
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}.
     */
    public final void setEnabled(final boolean enabled) throws SecurityException {
        checkAccess(this);
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

    @Override
    public final void protect(Object protectionKey) throws SecurityException {
        if (protectKeyUpdater.compareAndSet(this, null, protectionKey)) {
            return;
        }
        throw new SecurityException("Log handler already protected");
    }

    @Override
    public final void unprotect(Object protectionKey) throws SecurityException {
        if (protectKeyUpdater.compareAndSet(this, protectionKey, null)) {
            return;
        }
        throw accessDenied();
    }

    @Override
    public final void enableAccess(Object protectKey) {
        if (protectKey == this.protectKey) {
            granted.set(Boolean.TRUE);
        }
    }

    @Override
    public final void disableAccess() {
        granted.remove();
    }

    private static SecurityException accessDenied() {
        return new SecurityException("Log handler modification access denied");
    }

    /**
     * Check access.
     *
     * @deprecated use {@link #checkAccess(ExtHandler)}
     *
     * @throws SecurityException if a security manager is installed and the caller does not have the {@code "control" LoggingPermission}
     */
    @Deprecated
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
     *                           LoggingPermission(control)} or the handler is {@link #protect(Object) protected}.
     */
    protected static void checkAccess(final ExtHandler handler) throws SecurityException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONTROL_PERMISSION);
        }
        if (handler.protectKey != null && handler.granted.get() == null) {
            throw accessDenied();
        }
    }

    /**
     * Flush all child handlers.
     */
    @Override
    public void flush() {
        for (Handler handler : handlers) try {
            handler.flush();
        } catch (Exception ex) {
            reportError("Failed to flush child handler", ex, ErrorManager.FLUSH_FAILURE);
        } catch (Throwable ignored) {}
    }

    /**
     * Close all child handlers.
     */
    @Override
    public void close() throws SecurityException {
        checkAccess(this);
        for (Handler handler : handlers) try {
            handler.close();
        } catch (Exception ex) {
            reportError("Failed to close child handler", ex, ErrorManager.CLOSE_FAILURE);
        } catch (Throwable ignored) {}
    }

    @Override
    public void setFormatter(final Formatter newFormatter) throws SecurityException {
        checkAccess(this);
        super.setFormatter(newFormatter);
    }

    @Override
    public void setFilter(final Filter newFilter) throws SecurityException {
        checkAccess(this);
        super.setFilter(newFilter);
    }

    @Override
    public void setEncoding(final String encoding) throws SecurityException, UnsupportedEncodingException {
        checkAccess(this);
        super.setEncoding(encoding);
    }

    @Override
    public void setErrorManager(final ErrorManager em) {
        checkAccess(this);
        super.setErrorManager(em);
    }

    @Override
    public void setLevel(final Level newLevel) throws SecurityException {
        checkAccess(this);
        super.setLevel(newLevel);
    }
}
