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

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;

import io.smallrye.common.constraint.Assert;
import io.smallrye.common.ref.PhantomReference;
import io.smallrye.common.ref.Reaper;
import io.smallrye.common.ref.Reference;

/**
 * A node in the tree of logger names.  Maintains weak references to children and a strong reference to its parent.
 */
final class LoggerNode implements AutoCloseable {

    private static final Reaper<Logger, LoggerNode> REAPER = new Reaper<Logger, LoggerNode>() {
        @Override
        public void reap(Reference<Logger, LoggerNode> reference) {
            reference.getAttachment().activeLoggers.remove(reference);
        }
    };
    private static final StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];
    private static final VarHandle attachmentHandle = ConstantBootstraps.fieldVarHandle(MethodHandles.lookup(), "attachments", VarHandle.class, LoggerNode.class, Map.class);

    /**
     * The log context.
     */
    private final LogContext context;
    /**
     * The parent node, or {@code null} if this is the root logger node.
     */
    private final LoggerNode parent;
    /**
     * The fully-qualified name of this logger.
     */
    private final String fullName;

    /**
     * The map of names to child nodes.  The child node references are weak.
     */
    private final ConcurrentMap<String, LoggerNode> children;

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
     * Flag to specify whether parent filters are used.
     */
    private volatile boolean useParentFilter = false;

    /**
     * The set of phantom references to active loggers.
     */
    private final Set<Reference<Logger, LoggerNode>> activeLoggers = ConcurrentHashMap.newKeySet();

    /**
     * The first attachment key.
     */
    private Logger.AttachmentKey<?> attachmentKey1;

    /**
     * The first attachment value.
     */
    private Object attachmentValue1;

    /**
     * The second attachment key.
     */
    private Logger.AttachmentKey<?> attachmentKey2;

    /**
     * The second attachment value.
     */
    private Object attachmentValue2;

    private volatile Map<Logger.AttachmentKey<?>, Object> attachments = Collections.emptyMap();

    /**
     * The atomic updater for the {@link #handlers} field.
     */
    private static final AtomicArray<LoggerNode, Handler> handlersUpdater = AtomicArray.create(AtomicReferenceFieldUpdater.newUpdater(LoggerNode.class, Handler[].class, "handlers"), Handler.class);

    /**
     * The actual level.  May only be modified when the context's level change lock is held; in addition, changing
     * this field must be followed immediately by recursively updating the effective loglevel of the child tree.
     */
    private volatile java.util.logging.Level level;
    /**
     * The effective level.  May only be modified when the context's level change lock is held; in addition, changing
     * this field must be followed immediately by recursively updating the effective loglevel of the child tree.
     */
    private volatile int effectiveLevel = Logger.INFO_INT;

    /**
     * Construct a new root instance.
     *
     * @param context the logmanager
     */
    LoggerNode(final LogContext context) {
        parent = null;
        fullName = "";
        this.context = context;
        final LogContextInitializer initializer = context.getInitializer();
        final Level initialLevel = initializer.getInitialLevel(fullName);
        if (initialLevel != null) {
            level = initialLevel;
            effectiveLevel = initialLevel.intValue();
        }
        handlers = safeCloneHandlers(initializer.getInitialHandlers(fullName));
        children = context.createChildMap();
    }

    /**
     * Construct a child instance.
     *
     * @param context the logmanager
     * @param parent the parent node
     * @param nodeName the name of this subnode
     */
    private LoggerNode(LogContext context, LoggerNode parent, String nodeName) {
        nodeName = nodeName.trim();
        if (nodeName.length() == 0 && parent == null) {
            throw new IllegalArgumentException("nodeName is empty, or just whitespace and has no parent");
        }
        this.parent = parent;
        if (parent.parent == null) {
            if (nodeName.isEmpty()) {
                fullName = ".";
            } else {
                fullName = nodeName;
            }
        } else {
            fullName = parent.fullName + "." + nodeName;
        }
        this.context = context;
        final LogContextInitializer initializer = context.getInitializer();
        final Level initialLevel = initializer.getInitialLevel(fullName);
        if (initialLevel != null) {
            level = initialLevel;
            effectiveLevel = initialLevel.intValue();
        } else {
            effectiveLevel = parent.effectiveLevel;
        }
        handlers = safeCloneHandlers(initializer.getInitialHandlers(fullName));
        children = context.createChildMap();
    }

    static Handler[] safeCloneHandlers(Handler... initialHandlers) {
        if (initialHandlers == null || initialHandlers.length == 0) {
            return LogContextInitializer.NO_HANDLERS;
        }
        final Handler[] clone = initialHandlers.clone();
        final int length = clone.length;
        for (int i = 0; i < length; i++) {
            if (clone[i] == null) {
                // our clone contains nulls; we have to clone again to be safe
                int cnt;
                for (cnt = 1, i ++; i < length; i ++) {
                    if (clone[i] == null) cnt ++;
                }
                final int newLen = length - cnt;
                if (newLen == 0) {
                    return LogContextInitializer.NO_HANDLERS;
                }
                final Handler[] newClone = new Handler[newLen];
                for (int j = 0, k = 0; j < length; j ++) {
                    if (clone[j] != null) newClone[k++] = clone[j];
                }
                return newClone;
            }
        }
        // original contained no nulls, so carry on
        return clone;
    }

    @Override
    public void close() {
        synchronized (context.treeLock) {
            // Reset everything to defaults
            filter = null;
            if ("".equals(fullName)) {
                level = Level.INFO;
                effectiveLevel = Level.INFO.intValue();
            } else {
                level = null;
                effectiveLevel = Level.INFO.intValue();
            }
            handlersUpdater.clear(this);
            useParentFilter = false;
            useParentHandlers = true;
            attachmentKey1 = null;
            attachmentValue1 = null;
            attachmentKey2 = null;
            attachmentValue2 = null;
            attachmentHandle.set(this, Collections.emptyMap());
            children.clear();
        }
    }

    /**
     * Get or create a relative logger node.  The name is relatively qualified to this node.
     *
     * @param name the name
     * @return the corresponding logger node
     */
    LoggerNode getOrCreate(final String name) {
        if (name == null || name.length() == 0) {
            return this;
        } else {
            int i = name.indexOf('.');
            final String nextName = i == -1 ? name : name.substring(0, i);
            LoggerNode nextNode = children.get(nextName);
            if (nextNode == null) {
                nextNode = new LoggerNode(context, this, nextName);
                LoggerNode appearingNode = children.putIfAbsent(nextName, nextNode);
                if (appearingNode != null) {
                    nextNode = appearingNode;
                }
            }
            if (i == -1) {
                return nextNode;
            } else {
                return nextNode.getOrCreate(name.substring(i + 1));
            }
        }
    }

    /**
     * Get a relative logger, if it exists.
     *
     * @param name the name
     * @return the corresponding logger
     */
    LoggerNode getIfExists(final String name) {
        if (name == null || name.length() == 0) {
            return this;
        } else {
            int i = name.indexOf('.');
            final String nextName = i == -1 ? name : name.substring(0, i);
            LoggerNode nextNode = children.get(nextName);
            if (nextNode == null) {
                return null;
            }
            if (i == -1) {
                return nextNode;
            } else {
                return nextNode.getIfExists(name.substring(i + 1));
            }
        }
    }

    Logger createLogger() {
        final Logger logger = new Logger(this, fullName);
        activeLoggers.add(new PhantomReference<Logger, LoggerNode>(logger, LoggerNode.this, REAPER));
        return logger;
    }

    /**
     * Get the children of this logger.
     *
     * @return the children
     */
    Collection<LoggerNode> getChildren() {
        return children.values();
    }

    /**
     * Get the log context.
     *
     * @return the log context
     */
    LogContext getContext() {
        return context;
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
            for (LoggerNode node : children.values()) {
                if (node != null) {
                    node.setEffectiveLevel(newLevel);
                }
            }
        }
    }

    void setFilter(final Filter filter) {
        this.filter = filter;
    }

    Filter getFilter() {
        return filter;
    }

    boolean getUseParentFilters() {
        return useParentFilter;
    }

    void setUseParentFilters(final boolean useParentFilter) {
        this.useParentFilter = useParentFilter;
    }

    int getEffectiveLevel() {
        return effectiveLevel;
    }

    Handler[] getHandlers() {
        return handlers;
    }

    Handler[] clearHandlers() {
        final Handler[] handlers = this.handlers;
        handlersUpdater.clear(this);
        return handlers.length > 0 ? handlers.clone() : handlers;
    }

    void removeHandler(final Handler handler) {
        handlersUpdater.remove(this, handler, true);
    }

    void addHandler(final Handler handler) {
        handlersUpdater.add(this, handler);
    }

    Handler[] setHandlers(final Handler[] handlers) {
        return handlersUpdater.getAndSet(this, handlers);
    }

    boolean compareAndSetHandlers(final Handler[] oldHandlers, final Handler[] newHandlers) {
        return handlersUpdater.compareAndSet(this, oldHandlers, newHandlers);
    }

    boolean getUseParentHandlers() {
        return useParentHandlers;
    }

    void setUseParentHandlers(final boolean useParentHandlers) {
        this.useParentHandlers = useParentHandlers;
    }

    void publish(final ExtLogRecord record) {
        for (Handler handler : handlers) try {
            handler.publish(record);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            ErrorManager errorManager = AccessController.doPrivileged(new PrivilegedAction<ErrorManager>() {
                @Override
                public ErrorManager run() {
                    return handler.getErrorManager();
                }
            });
            if (errorManager != null) {
                Exception e;
                if (t instanceof Exception) {
                    e = (Exception) t;
                } else {
                    e = new UndeclaredThrowableException(t);
                    e.setStackTrace(EMPTY_STACK);
                }
                try {
                    errorManager.error("Handler publication threw an exception", e, ErrorManager.WRITE_FAILURE);
                } catch (Throwable t2) {
                    StandardOutputStreams.printError(t2, "Handler.reportError caught an exception");
                }
            }
        }
        if (useParentHandlers) {
            final LoggerNode parent = this.parent;
            if (parent != null) parent.publish(record);
        }
    }

    void setLevel(final Level newLevel) {
        final LogContext context = this.context;
        final Object lock = context.treeLock;
        synchronized (lock) {
            final int oldEffectiveLevel = effectiveLevel;
            final int newEffectiveLevel;
            if (newLevel != null) {
                level = newLevel;
                newEffectiveLevel = newLevel.intValue();
            } else {
                final LoggerNode parent = this.parent;
                if (parent == null) {
                    level = Level.INFO;
                    newEffectiveLevel = Logger.INFO_INT;
                } else {
                    level = null;
                    newEffectiveLevel = parent.effectiveLevel;
                }
            }
            effectiveLevel = newEffectiveLevel;
            if (oldEffectiveLevel != newEffectiveLevel) {
                // our level changed, recurse down to children
                for (LoggerNode node : children.values()) {
                    if (node != null) {
                        node.setEffectiveLevel(newEffectiveLevel);
                    }
                }
            }
        }
    }

    Level getLevel() {
        return level;
    }

    @SuppressWarnings({ "unchecked" })
    <V> V getAttachment(final Logger.AttachmentKey<V> key) {
        Assert.checkNotNullParam("key", key);
        return (V) attachments.get(key);
    }

    @SuppressWarnings({ "unchecked" })
    <V> V attach(final Logger.AttachmentKey<V> key, final V value) {
        Assert.checkNotNullParam("key", key);
        Assert.checkNotNullParam("value", value);

        Map<Logger.AttachmentKey<?>, Object> oldAttachments;
        Map<Logger.AttachmentKey<?>, Object> newAttachments;
        V old;
        do {
            oldAttachments = attachments;
            newAttachments = new HashMap<>(oldAttachments);
            old = (V) newAttachments.put(key, value);
        } while (!attachmentHandle.compareAndSet(this, oldAttachments, Map.copyOf(newAttachments)));
        return old;
    }

    @SuppressWarnings({ "unchecked" })
    <V> V attachIfAbsent(final Logger.AttachmentKey<V> key, final V value) {
        Assert.checkNotNullParam("key", key);
        Assert.checkNotNullParam("value", value);
        Map<Logger.AttachmentKey<?>, Object> oldAttachments;
        Map<Logger.AttachmentKey<?>, Object> newAttachments;
        do {
            oldAttachments = attachments;
            if (oldAttachments.containsKey(key)) {
                return (V) oldAttachments.get(key);
            }
            newAttachments = new HashMap<>(oldAttachments);
            newAttachments.put(key, value);
        } while (!attachmentHandle.compareAndSet(this, oldAttachments, Map.copyOf(newAttachments)));
        return null;
    }

    @SuppressWarnings({ "unchecked" })
    public <V> V detach(final Logger.AttachmentKey<V> key) {
        Assert.checkNotNullParam("key", key);
        Map<Logger.AttachmentKey<?>, Object> oldAttachments;
        Map<Logger.AttachmentKey<?>, Object> newAttachments;
        V result;
        int size;
        do {
            oldAttachments = attachments;
            result = (V) oldAttachments.get(key);
            if (result == null) {
                return null;
            }
            size = oldAttachments.size();
            if (size == 1) {
                // special case - the new map is empty
                newAttachments = Collections.emptyMap();
            } else {
                newAttachments = new HashMap<>(oldAttachments);
            }
        } while (!attachmentHandle.compareAndSet(this, oldAttachments, size == 1 ? newAttachments : Map.copyOf(newAttachments)));
        return result;
    }

    String getFullName() {
        return fullName;
    }

    LoggerNode getParent() {
        return parent;
    }

    /**
     * Checks the filter to see if the record is loggable. If the {@link #getUseParentFilters()} is set to {@code true}
     * the parent loggers are checked.
     *
     * @param record the log record to check against the filter
     *
     * @return {@code true} if the record is loggable, otherwise {@code false}
     */
    boolean isLoggable(final ExtLogRecord record) {
        if (!useParentFilter) {
            final Filter filter = this.filter;
            return filter == null || filter.isLoggable(record);
        }
        final LogContext context = this.context;
        final Object lock = context.treeLock;
        synchronized (lock) {
            return isLoggable(this, record);
        }
    }

    private static boolean isLoggable(final LoggerNode loggerNode, final ExtLogRecord record) {
        if (loggerNode == null) {
            return true;
        }
        final Filter filter = loggerNode.filter;
        return !(filter != null && !filter.isLoggable(record)) && (!loggerNode.useParentFilter || isLoggable(loggerNode.getParent(), record));
    }

    Enumeration<String> getLoggerNames() {
        return new Enumeration<String>() {
            final Iterator<LoggerNode> children = getChildren().iterator();
            String next = activeLoggers.isEmpty() ? null : fullName;
            Enumeration<String> sub;

            @Override
            public boolean hasMoreElements() {
                while (next == null) {
                    while (sub == null) {
                        if (children.hasNext()) {
                            final LoggerNode child = children.next();
                            sub = child.getLoggerNames();
                        } else {
                            return false;
                        }
                    }
                    if (sub.hasMoreElements()) {
                        next = sub.nextElement();
                        return true;
                    } else {
                        sub = null;
                    }
                }
                return true;
            }

            @Override
            public String nextElement() {
                try {
                    return next;
                } finally {
                    next = null;
                }
            }
        };
    }

    static IllegalArgumentException attachmentsFull() {
        return new IllegalArgumentException("Attachments map is full");
    }
}
