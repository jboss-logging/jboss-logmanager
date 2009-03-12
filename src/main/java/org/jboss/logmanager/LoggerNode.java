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
import java.util.concurrent.ConcurrentMap;
import static org.jboss.logmanager.ConcurrentReferenceHashMap.ReferenceType.WEAK;
import static org.jboss.logmanager.ConcurrentReferenceHashMap.ReferenceType.STRONG;

/**
 * A node in the tree of logger names.  Maintains weak references to children and a strong reference to its parent.
 */
class LoggerNode {

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
     * A weak reference to the logger instance.  Protected by {@code this}.
     */
    private WeakReference<Logger> loggerRef = null;

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
        parent.children.put(nodeName, this);
        this.context = context;
    }

    /**
     * Get or create a relative logger node.  The name is relatively qualified to this node.
     *
     * @param name the name
     * @return the corresponding logger node
     */
    LoggerNode getOrCreate(String name) {
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
     * Get or create a logger instance for this node.
     *
     * @return a logger instance
     */
    Logger getOrCreateLogger() {
        synchronized(this) {
            Logger instance = loggerRef == null ? null : loggerRef.get();
            if (instance == null) {
                instance = new Logger(this, fullName);
                loggerRef = new WeakReference<Logger>(instance);
                instance.setLevel(null);
            }
            return instance;
        }
    }

    /**
     * Return the logger instance of the parent logger node, or {@code null} if this is the root logger node.
     *
     * @return the parent logger instance, or {@code null} for none
     */
    Logger getParentLogger() {
        LoggerNode node = parent;
        while (node != null) {
            synchronized(node) {
                final Logger instance = node.loggerRef == null ? null : node.loggerRef.get();
                if (instance != null) {
                    return instance;
                }
                node = node.parent;
            }
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
     * log node nesting depth so stack use should not be an issue.  Must only be called while the logmanager's level
     * change lock is held.
     *
     * @param newLevel the new effective level
     */
    void updateChildEffectiveLevel(int newLevel) {
        for (LoggerNode node : children.values()) {
            if (node != null) {
                synchronized (node) {
                    final WeakReference<Logger> loggerRef = node.loggerRef;
                    if (loggerRef != null) {
                        final Logger instance = loggerRef.get();
                        if (instance != null) {
                            instance.setEffectiveLevel(newLevel);
                        }
                    }
                }
            }
        }
    }
}
