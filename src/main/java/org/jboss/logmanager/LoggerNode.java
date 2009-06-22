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

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.security.AccessController;
import java.security.PrivilegedAction;
import static org.jboss.logmanager.ConcurrentReferenceHashMap.ReferenceType.STRONG;
import static org.jboss.logmanager.ConcurrentReferenceHashMap.ReferenceType.WEAK;

/**
 * A node in the tree of logger names.  Maintains weak references to children and a strong reference to its parent.
 */
final class LoggerNode {

    /**
     * The log manager.
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
     * A weak reference to the logger instance.  Only update using {@link #loggerRefUpdater}.
     */
    private volatile LoggerRef loggerRef = null;

    /**
     * The atomic updater for {@link #loggerRef}.
     */
    private static final AtomicReferenceFieldUpdater<LoggerNode, LoggerRef> loggerRefUpdater = AtomicReferenceFieldUpdater.newUpdater(LoggerNode.class, LoggerRef.class, "loggerRef");

    /**
     * The map of names to child nodes.  The child node references are weak.
     */
    private final ConcurrentMap<String, LoggerNode> children = new ConcurrentReferenceHashMap<String, LoggerNode>(8, STRONG, WEAK);

    /**
     * Construct a new root instance.
     *
     * @param context the logmanager
     */
    LoggerNode(final LogContext context) {
        parent = null;
        fullName = "";
        this.context = context;
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
        if (nodeName.length() == 0) {
            throw new IllegalArgumentException("nodeName is empty, or just whitespace");
        }
        this.parent = parent;
        if (parent.parent == null) {
            fullName = nodeName;
        } else {
            fullName = parent.fullName + "." + nodeName;
        }
        this.context = context;
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

    /**
     * Get or create a logger instance for this node.
     *
     * @return a logger instance
     */
    Logger getOrCreateLogger() {
        final String fullName = this.fullName;
        final LoggerNode parent = this.parent;
        for (;;) {
            final LoggerRef loggerRef = this.loggerRef;
            if (loggerRef != null) {
                final Logger logger = loggerRef.get();
                if (logger != null) {
                    return logger;
                }
            }
            final Logger logger = createLogger(fullName);
            if (loggerRefUpdater.compareAndSet(this, loggerRef, parent == null ? new StrongLoggerRef(logger) : new WeakLoggerRef(logger))) {
                // initialize the effective level
                logger.setLevel(null);
                return logger;
            }
        }
    }

    private Logger createLogger(final String fullName) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return AccessController.doPrivileged(new PrivilegedAction<Logger>() {
                public Logger run() {
                    return new Logger(LoggerNode.this, fullName);
                }
            });
        } else {
            return new Logger(this, fullName);
        }
    }

    /**
     * Get a logger instance for this node.
     *
     * @return a logger instance
     */
    Logger getLogger() {
        final LoggerRef loggerRef = this.loggerRef;
        return loggerRef == null ? null : loggerRef.get();
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
     * Return the logger instance of the parent logger node, or {@code null} if this is the root logger node.
     *
     * @return the parent logger instance, or {@code null} for none
     */
    Logger getParentLogger() {
        LoggerNode node = parent;
        while (node != null) {
            final Logger instance = node.getLogger();
            if (instance != null) {
                return instance;
            }
            node = node.parent;
        }
        return null;
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
     * Recursively update the effective log level of all log instances on all children.  The recursion depth will be proportionate to the
     * log node nesting depth so stack use should not be an issue.  Must only be called while the log context's level
     * change lock is held.
     *
     * @param newLevel the new effective level
     */
    void updateChildEffectiveLevel(int newLevel) {
        for (LoggerNode node : children.values()) {
            if (node != null) {
                final Logger instance = node.getLogger();
                if (instance != null) {
                    instance.setEffectiveLevel(newLevel);
                }
            }
        }
    }

    private interface LoggerRef {
        Logger get();
    }

    private static final class WeakLoggerRef extends WeakReference<Logger> implements LoggerRef {
        private WeakLoggerRef(Logger referent) {
            super(referent);
        }
    }

    private static final class StrongLoggerRef implements LoggerRef {
        private final Logger logger;

        private StrongLoggerRef(final Logger logger) {
            this.logger = logger;
        }

        public Logger get() {
            return logger;
        }
    }
}
