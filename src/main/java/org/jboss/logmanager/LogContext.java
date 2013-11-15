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

import java.lang.ref.WeakReference;
import java.security.Permission;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import java.util.logging.Level;
import java.util.logging.LoggingMXBean;
import java.util.logging.LoggingPermission;

/**
 * A logging context, for producing isolated logging environments.
 */
public final class LogContext {
    private static final LogContext SYSTEM_CONTEXT = new LogContext();

    static final Permission CREATE_CONTEXT_PERMISSION = new RuntimePermission("createLogContext", null);
    static final Permission SET_CONTEXT_SELECTOR_PERMISSION = new RuntimePermission("setLogContextSelector", null);
    static final Permission CONTROL_PERMISSION = new LoggingPermission("control", null);

    private final LoggerNode rootLogger;
    @SuppressWarnings({ "ThisEscapedInObjectConstruction" })
    private final LoggingMXBean mxBean = new LoggingMXBeanImpl(this);

    /**
     * This lazy holder class is required to prevent a problem due to a LogContext instance being constructed
     * before the class init is complete.
     */
    private static final class LazyHolder {
        private static final HashMap<String, LevelRef> INITIAL_LEVEL_MAP;

        private LazyHolder() {
        }

        private static void addStrong(Map<String, LevelRef> map, Level level) {
            map.put(level.getName().toUpperCase(), new StrongLevelRef(level));
        }

        static {
            final HashMap<String, LevelRef> map = new HashMap<String, LevelRef>();
            addStrong(map, Level.OFF);
            addStrong(map, Level.ALL);
            addStrong(map, Level.SEVERE);
            addStrong(map, Level.WARNING);
            addStrong(map, Level.CONFIG);
            addStrong(map, Level.INFO);
            addStrong(map, Level.FINE);
            addStrong(map, Level.FINER);
            addStrong(map, Level.FINEST);

            addStrong(map, org.jboss.logmanager.Level.FATAL);
            addStrong(map, org.jboss.logmanager.Level.ERROR);
            addStrong(map, org.jboss.logmanager.Level.WARN);
            addStrong(map, org.jboss.logmanager.Level.INFO);
            addStrong(map, org.jboss.logmanager.Level.DEBUG);
            addStrong(map, org.jboss.logmanager.Level.TRACE);

            INITIAL_LEVEL_MAP = map;
        }
    }

    private final AtomicReference<Map<String, LevelRef>> levelMapReference;

    /**
     * This lock is taken any time a change is made which affects multiple nodes in the hierarchy.
     */
    final Lock treeLock = new ReentrantLock(false);

    LogContext() {
        levelMapReference = new AtomicReference<Map<String, LevelRef>>(LazyHolder.INITIAL_LEVEL_MAP);
        rootLogger = new LoggerNode(this);
    }

    /**
     * Create a new log context.  If a security manager is installed, the caller must have the {@code "createLogContext"}
     * {@link RuntimePermission RuntimePermission} to invoke this method.
     *
     * @return a new log context
     */
    public static LogContext create() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_CONTEXT_PERMISSION);
        }
        return new LogContext();
    }

    /**
     * Get a logger with the given name from this logging context.
     *
     * @param name the logger name
     * @return the logger instance
     * @see java.util.logging.LogManager#getLogger(String)
     */
    public Logger getLogger(String name) {
        return rootLogger.getOrCreate(name).createLogger();
    }

    /**
     * Get a logger with the given name from this logging context, if a logger node exists at that location.
     *
     * @param name the logger name
     * @return the logger instance, or {@code null} if no such logger node exists
     */
    public Logger getLoggerIfExists(String name) {
        final LoggerNode node = rootLogger.getIfExists(name);
        return node == null ? null : node.createLogger();
    }

    /**
     * Get a logger attachment for a logger name, if it exists.
     *
     * @param loggerName the logger name
     * @param key the attachment key
     * @param <V> the attachment value type
     * @return the attachment or {@code null} if the logger or the attachment does not exist
     */
    public <V> V getAttachment(String loggerName, Logger.AttachmentKey<V> key) {
        final LoggerNode node = rootLogger.getIfExists(loggerName);
        if (node == null) return null;
        return node.getAttachment(key);
    }

    /**
     * Get the {@code LoggingMXBean} associated with this log context.
     *
     * @return the {@code LoggingMXBean} instance
     */
    public LoggingMXBean getLoggingMXBean() {
        return mxBean;
    }

    /**
     * Get the level for a name.
     *
     * @param name the name
     * @return the level
     * @throws IllegalArgumentException if the name is not known
     */
    public Level getLevelForName(String name) throws IllegalArgumentException {
        if (name != null) {
            final Map<String, LevelRef> map = levelMapReference.get();
            final LogContext.LevelRef levelRef = map.get(name);
            if (levelRef != null) {
                final Level level = levelRef.get();
                if (level != null) {
                    return level;
                }
            }
        }
        throw new IllegalArgumentException("Unknown level \"" + name + "\"");
    }

    /**
     * Register a level instance with this log context.  The level can then be looked up by name.  Only a weak
     * reference to the level instance will be kept.  Any previous level registration for the given level's name
     * will be overwritten.
     *
     * @param level the level to register
     */
    public void registerLevel(Level level) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONTROL_PERMISSION);
        }
        for (;;) {
            final Map<String, LevelRef> oldLevelMap = levelMapReference.get();
            final Map<String, LevelRef> newLevelMap = new HashMap<String, LevelRef>(oldLevelMap.size());
            for (Map.Entry<String, LevelRef> entry : oldLevelMap.entrySet()) {
                final String name = entry.getKey();
                final LogContext.LevelRef levelRef = entry.getValue();
                if (levelRef.get() != null) {
                    newLevelMap.put(name, levelRef);
                }
            }
            newLevelMap.put(level.getName(), new WeakLevelRef(level));
            if (levelMapReference.compareAndSet(oldLevelMap, newLevelMap)) {
                return;
            }
        }
    }

    /**
     * Unregister a previously registered level.  Log levels that are not registered may still be used, they just will
     * not be findable by name.
     *
     * @param level the level to unregister
     */
    public void unregisterLevel(Level level) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONTROL_PERMISSION);
        }
        for (;;) {
            final Map<String, LevelRef> oldLevelMap = levelMapReference.get();
            final LevelRef oldRef = oldLevelMap.get(level.getName());
            if (oldRef == null || oldRef.get() != level) {
                // not registered, or the registration expired naturally
                return;
            }
            final Map<String, LevelRef> newLevelMap = new HashMap<String, LevelRef>(oldLevelMap.size());
            for (Map.Entry<String, LevelRef> entry : oldLevelMap.entrySet()) {
                final String name = entry.getKey();
                final LevelRef levelRef = entry.getValue();
                final Level oldLevel = levelRef.get();
                if (oldLevel != null && oldLevel != level) {
                    newLevelMap.put(name, levelRef);
                }
            }
            newLevelMap.put(level.getName(), new WeakLevelRef(level));
            if (levelMapReference.compareAndSet(oldLevelMap, newLevelMap)) {
                return;
            }
        }
    }

    /**
     * Get the system log context.
     *
     * @return the system log context
     */
    public static LogContext getSystemLogContext() {
        return SYSTEM_CONTEXT;
    }

    /**
     * The default log context selector, which always returns the system log context.
     */
    public static final LogContextSelector DEFAULT_LOG_CONTEXT_SELECTOR = new LogContextSelector() {
        public LogContext getLogContext() {
            return SYSTEM_CONTEXT;
        }
    };

    private static volatile LogContextSelector logContextSelector = DEFAULT_LOG_CONTEXT_SELECTOR;

    /**
     * Get the currently active log context.
     *
     * @return the currently active log context
     */
    public static LogContext getLogContext() {
        return logContextSelector.getLogContext();
    }

    /**
     * Set a new log context selector.  If a security manager is installed, the caller must have the {@code "setLogContextSelector"}
     * {@link RuntimePermission RuntimePermission} to invoke this method.
     *
     * @param newSelector the new selector.
     */
    public static void setLogContextSelector(LogContextSelector newSelector) {
        if (newSelector == null) {
            throw new NullPointerException("newSelector is null");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_CONTEXT_SELECTOR_PERMISSION);
        }
        logContextSelector = newSelector;
    }

    static void checkAccess() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONTROL_PERMISSION);
        }
    }

    LoggerNode getRootLoggerNode() {
        return rootLogger;
    }

    private interface LevelRef {
        Level get();
    }

    private static final class WeakLevelRef extends WeakReference<Level> implements LevelRef {
        private WeakLevelRef(final Level level) {
            super(level);
        }
    }

    private static final class StrongLevelRef implements LevelRef {
        private final Level level;

        private StrongLevelRef(final Level level) {
            this.level = level;
        }

        public Level get() {
            return level;
        }
    }
}
