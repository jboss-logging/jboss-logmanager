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

import java.lang.ref.WeakReference;
import java.security.Permission;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.Level;
import java.util.logging.LoggingMXBean;
import java.util.logging.LoggingPermission;

/**
 * A logging context, for producing isolated logging environments.
 */
public final class LogContext implements Protectable {

    private static final LogContext SYSTEM_CONTEXT = new LogContext(false);

    static final Permission CREATE_CONTEXT_PERMISSION = new RuntimePermission("createLogContext", null);
    static final Permission SET_CONTEXT_SELECTOR_PERMISSION = new RuntimePermission("setLogContextSelector", null);
    static final Permission CONTROL_PERMISSION = new LoggingPermission("control", null);

    private final LoggerNode rootLogger;
    @SuppressWarnings({ "ThisEscapedInObjectConstruction" })
    private final LoggingMXBean mxBean = new LoggingMXBeanImpl(this);
    private final boolean strong;
    private final ConcurrentSkipListMap<String, AtomicInteger> loggerNames;

    @SuppressWarnings("unused")
    private volatile Object protectKey;

    private final ThreadLocal<Boolean> granted = new InheritableThreadLocal<Boolean>();

    private static final AtomicReferenceFieldUpdater<LogContext, Object> protectKeyUpdater = AtomicReferenceFieldUpdater.newUpdater(LogContext.class, Object.class, "protectKey");

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
    private final Bootstrap bootstrap;

    /**
     * This lock is taken any time a change is made which affects multiple nodes in the hierarchy.
     */
    final Object treeLock = new Object();

    LogContext(final boolean strong) {
        this(strong, BootstrapConfiguration.create());
    }

    LogContext(final boolean strong, final BootstrapConfiguration bootstrapConfig) {
        this.strong = strong;
        levelMapReference = new AtomicReference<Map<String, LevelRef>>(LazyHolder.INITIAL_LEVEL_MAP);
        rootLogger = new LoggerNode(this);
        loggerNames = new ConcurrentSkipListMap<String, AtomicInteger>();
        this.bootstrap = bootstrapConfig == null ? BootstrapConfiguration.create().build(rootLogger) :
                bootstrapConfig.build(rootLogger);
    }

    /**
     * Create a new log context.  If a security manager is installed, the caller must have the {@code "createLogContext"}
     * {@link RuntimePermission RuntimePermission} to invoke this method.
     *
     * @param strong {@code true} if the context should use strong references, {@code false} to use (default) weak
     *      references for automatic logger GC
     * @return a new log context
     */
    public static LogContext create(boolean strong) {
        return create(strong, null);
    }

    /**
     * Create a new log context.  If a security manager is installed, the caller must have the {@code "createLogContext"}
     * {@link RuntimePermission RuntimePermission} to invoke this method.
     *
     * @param strong {@code true} if the context should use strong references, {@code false} to use (default) weak
     *      references for automatic logger GC
     * @param bootstrapConfig the configuration for bootstrapping this log context
     * @return a new log context
     */
    public static LogContext create(boolean strong, final BootstrapConfiguration bootstrapConfig) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_CONTEXT_PERMISSION);
        }
        return new LogContext(strong, bootstrapConfig);
    }

    /**
     * Create a new log context.  If a security manager is installed, the caller must have the {@code "createLogContext"}
     * {@link RuntimePermission RuntimePermission} to invoke this method.
     *
     * @param bootstrapConfig the configuration for bootstrapping this log context
     * @return a new log context
     */
    public static LogContext create(final BootstrapConfiguration bootstrapConfig) {
        return create(false, bootstrapConfig);
    }

    /**
     * Create a new log context.  If a security manager is installed, the caller must have the {@code "createLogContext"}
     * {@link RuntimePermission RuntimePermission} to invoke this method.
     *
     * @return a new log context
     */
    public static LogContext create() {
        return create(false);
    }

    /**
     * Get a logger with the given name from this logging context.
     *
     * @param name the logger name
     * @return the logger instance
     * @see java.util.logging.LogManager#getLogger(String)
     */
    public Logger getLogger(String name) {
        final LoggerNode node = rootLogger.getOrCreate(name);
        return node.createLogger();
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

    @Override
    public void protect(Object protectionKey) throws SecurityException {
        if (protectKeyUpdater.compareAndSet(this, null, protectionKey)) {
            return;
        }
        throw new SecurityException("Log context already protected");
    }

    @Override
    public void unprotect(Object protectionKey) throws SecurityException {
        if (protectKeyUpdater.compareAndSet(this, protectionKey, null)) {
            return;
        }
        throw accessDenied();
    }

    @Override
    public void enableAccess(Object protectKey) throws SecurityException {
        if (protectKey == this.protectKey) {
            granted.set(Boolean.TRUE);
        }
    }

    @Override
    public void disableAccess() {
        granted.remove();
    }

    /**
     * Returns an enumeration of the logger names that have been created. This does not return names of loggers that
     * may have been garbage collected. Logger names added after the enumeration has been retrieved may also be added to
     * the enumeration.
     *
     * @return an enumeration of the logger names
     *
     * @see java.util.logging.LogManager#getLoggerNames()
     */
    public Enumeration<String> getLoggerNames() {
        final Iterator<Entry<String, AtomicInteger>> iter = loggerNames.entrySet().iterator();
        return new Enumeration<String>() {
            String next = null;
            @Override
            public boolean hasMoreElements() {
                while (next == null) {
                    if (iter.hasNext()) {
                        final Entry<String, AtomicInteger> entry = iter.next();
                        if (entry.getValue().get() > 0) {
                            next = entry.getKey();
                            return true;
                        }
                    } else {
                        return false;
                    }
                }
                return next != null;
            }

            @Override
            public String nextElement() {
                if (!hasMoreElements()) {
                    throw new NoSuchElementException();
                }
                try {
                    return next;
                } finally {
                    next = null;
                }
            }
        };
    }

    /**
     * If this log context was bootstrapped this indicates that the configuration has been completed and the messages
     * can be replayed to the configured loggers.
     */
    public void configurationComplete() {
        bootstrap.complete();
    }

    /**
     * Returns the publisher to use with this log context.
     *
     * @return the publisher to use
     */
    LogRecordPublisher getLogRecordPublisher() {
        return bootstrap.getPublisher();
    }

    protected void incrementRef(final String name) {
        AtomicInteger counter = loggerNames.get(name);
        if (counter == null) {
            final AtomicInteger appearing = loggerNames.putIfAbsent(name, counter = new AtomicInteger());
            if (appearing != null) {
                counter = appearing;
            }
        }
        counter.incrementAndGet();
    }

    protected void decrementRef(final String name) {
        AtomicInteger counter = loggerNames.get(name);
        assert (counter != null && counter.get() > 0);
        counter.decrementAndGet();
    }

    private static SecurityException accessDenied() {
        return new SecurityException("Log context modification access denied");
    }

    static void checkSecurityAccess() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONTROL_PERMISSION);
        }
    }

    static void checkAccess(final LogContext logContext) {
        checkSecurityAccess();
        if (logContext.protectKey != null && logContext.granted.get() == null) {
            throw accessDenied();
        }
    }

    LoggerNode getRootLoggerNode() {
        return rootLogger;
    }

    ConcurrentMap<String, LoggerNode> createChildMap() {
        return strong ? new CopyOnWriteMap<String, LoggerNode>() : new CopyOnWriteWeakMap<String, LoggerNode>();
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
