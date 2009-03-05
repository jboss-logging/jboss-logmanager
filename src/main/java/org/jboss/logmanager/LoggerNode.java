package org.jboss.logmanager;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentMap;
import static org.jboss.logmanager.ConcurrentReferenceHashMap.ReferenceType.WEAK;
import static org.jboss.logmanager.ConcurrentReferenceHashMap.ReferenceType.STRONG;

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
     * A weak reference to the logger instance.  Protected by {@code this}.
     */
    private WeakReference<LoggerInstance> loggerRef = null;

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
        if (nodeName.trim().length() == 0) {
            throw new IllegalArgumentException("nodeName is empty, or just whitespace");
        }
        this.parent = parent;
        if (parent.parent == null) {
            fullName = nodeName;
        } else {
            fullName = parent.fullName + "." + nodeName;
        }
        synchronized(parent.children) {
            parent.children.put(nodeName, this);
        }
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
    LoggerInstance getOrCreateLogger() {
        synchronized(this) {
            LoggerInstance instance = loggerRef == null ? null : loggerRef.get();
            if (instance == null) {
                instance = new LoggerInstance(this, fullName);
                loggerRef = new WeakReference<LoggerInstance>(instance);
            }
            return instance;
        }
    }

    /**
     * Return the logger instance of the parent logger node, or {@code null} if this is the root logger node.
     *
     * @return the parent logger instance, or {@code null} for none
     */
    LoggerInstance getParentLogger() {
        LoggerNode node = parent;
        while (node != null) {
            synchronized(node) {
                final LoggerInstance instance = node.loggerRef == null ? null : node.loggerRef.get();
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
                final WeakReference<LoggerInstance> loggerRef = node.loggerRef;
                if (loggerRef != null) {
                    final LoggerInstance instance = loggerRef.get();
                    if (instance != null) {
                        instance.setEffectiveLevel(newLevel);
                    }
                }
            }
        }
    }
}
