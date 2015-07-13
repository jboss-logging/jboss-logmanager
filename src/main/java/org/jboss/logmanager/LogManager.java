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

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simplified log manager.  Designed to work around the (many) design flaws of the JDK platform log manager.
 */
public final class LogManager extends java.util.logging.LogManager {

    public static final String PER_THREAD_LOG_FILTER_KEY = "org.jboss.logmanager.useThreadLocalFilter";
    static final boolean PER_THREAD_LOG_FILTER;

    static {
        if (System.getSecurityManager() == null) {
            PER_THREAD_LOG_FILTER = Boolean.getBoolean(PER_THREAD_LOG_FILTER_KEY);
        } else {
            PER_THREAD_LOG_FILTER = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                    return Boolean.getBoolean(PER_THREAD_LOG_FILTER_KEY);
                }
            });
        }
    }

    private static class LocalFilterHolder {
        static final ThreadLocal<java.util.logging.Level> LOCAL_FILTER = new ThreadLocal<>();
    }

    /**
     * Construct a new logmanager instance.  Attempts to plug a known memory leak in {@link java.util.logging.Level} as
     * well.
     */
    public LogManager() {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @SuppressWarnings ({"unchecked"})
            public Void run() {
                /* This mysterious-looking hack is designed to trick JDK logging into not leaking classloaders and
                   so forth when adding levels, by simply shutting down the craptastic level name "registry" that it keeps.
                */
                final Class<java.util.logging.Level> lc = java.util.logging.Level.class;
                try {
                    synchronized(lc) {
                        final Field knownField = lc.getDeclaredField("known");
                        knownField.setAccessible(true);
                        final List<java.util.logging.Level> old = (List<java.util.logging.Level>) knownField.get(null);
                        if (! (old instanceof ReadOnlyArrayList)) {
                            knownField.set(null, new ReadOnlyArrayList<java.util.logging.Level>(Arrays.asList(
                                    Level.TRACE,
                                    Level.DEBUG,
                                    Level.INFO,
                                    Level.WARN,
                                    Level.ERROR,
                                    Level.FATAL,
                                    java.util.logging.Level.ALL,
                                    java.util.logging.Level.FINEST,
                                    java.util.logging.Level.FINER,
                                    java.util.logging.Level.FINE,
                                    java.util.logging.Level.INFO,
                                    java.util.logging.Level.CONFIG,
                                    java.util.logging.Level.WARNING,
                                    java.util.logging.Level.SEVERE,
                                    java.util.logging.Level.OFF
                            )));
                        }
                    }
                } catch (Throwable e) {
                    // ignore; just don't install
                }

                // OpenJDK uses a KnownLevel inner class with two static maps
                try {
                    final Class<?> knownLevelClass = Class.forName("java.util.logging.Level$KnownLevel");
                    synchronized (knownLevelClass) {
                        final Constructor<?> constructor = knownLevelClass.getConstructor(java.util.logging.Level.class);
                        constructor.setAccessible(true);
                        boolean doBuild = false;
                        boolean setNameToLevel = false;
                        boolean setIntToLevel = false;
                        // namesToLevels
                        final Field nameToLevels = knownLevelClass.getDeclaredField("nameToLevels");
                        nameToLevels.setAccessible(true);
                        // Current
                        final Map oldNameToLevels = (Map) nameToLevels.get(null);
                        if (!(oldNameToLevels instanceof ReadOnlyHashMap)) {
                            doBuild = true;
                            setNameToLevel = true;
                        }

                        final Field intToLevels = knownLevelClass.getDeclaredField("intToLevels");
                        intToLevels.setAccessible(true);
                        final Map oldIntToLevels = (Map) intToLevels.get(null);
                        if (!(oldIntToLevels instanceof ReadOnlyHashMap)) {
                            doBuild = true;
                            setIntToLevel = true;
                        }

                        if (doBuild) {
                            final KnownLevelBuilder builder = new KnownLevelBuilder(constructor)
                                    .add(Level.TRACE)
                                    .add(Level.DEBUG)
                                    .add(Level.INFO)
                                    .add(Level.WARN)
                                    .add(Level.ERROR)
                                    .add(Level.FATAL)
                                    .add(java.util.logging.Level.ALL)
                                    .add(java.util.logging.Level.FINEST)
                                    .add(java.util.logging.Level.FINER)
                                    .add(java.util.logging.Level.FINE)
                                    .add(java.util.logging.Level.INFO)
                                    .add(java.util.logging.Level.CONFIG)
                                    .add(java.util.logging.Level.WARNING)
                                    .add(java.util.logging.Level.SEVERE)
                                    .add(java.util.logging.Level.OFF);

                            if (setNameToLevel) {
                                nameToLevels.set(null, builder.toNameMap());
                            }
                            if (setIntToLevel) {
                                intToLevels.set(null, builder.toIntMap());
                            }
                        }
                    }

                } catch (Throwable e) {
                    // ignore
                }

                /* Next hack: the default Sun JMX implementation has a horribly inefficient log implementation which
                   kills performance if a custom logmanager is used.  We'll just blot that out.
                 */
                try {
                    final Class<?> traceManagerClass = Class.forName("com.sun.jmx.trace.Trace");
                    final Field outField = traceManagerClass.getDeclaredField("out");
                    outField.setAccessible(true);
                    outField.set(null, null);
                } catch (Throwable e) {
                    // ignore; just skip it
                }
                /* Next hack: Replace the crappy MXBean on the system logmanager, if it's there.
                 */
                final Class<java.util.logging.LogManager> lmc = java.util.logging.LogManager.class;
                try {
                    synchronized (lmc) {
                        final Field loggingMXBean = lmc.getDeclaredField("loggingMXBean");
                        loggingMXBean.setAccessible(true);
                        loggingMXBean.set(null, LogContext.getSystemLogContext().getLoggingMXBean());
                    }
                } catch (Throwable e) {
                    // ignore; just skip it
                }
                return null;
            }
        });
    }

    private static final class ReadOnlyArrayList<T> extends ArrayList<T> {

        private static final long serialVersionUID = -6048215349511680936L;

        private ReadOnlyArrayList(final Collection<? extends T> c) {
            super(c);
        }

        static <T> ReadOnlyArrayList<T> of(final Collection<? extends T> c) {
            return new ReadOnlyArrayList<T>(c);
        }

        public T set(final int index, final T element) {
            // ignore
            return null;
        }

        public T remove(final int index) {
            // ignore
            return null;
        }

        public boolean remove(final Object o) {
            // ignore
            return false;
        }

        public void clear() {
            // ignore
        }

        protected void removeRange(final int fromIndex, final int toIndex) {
            // ignore
        }

        public Iterator<T> iterator() {
            final Iterator<T> superIter = super.iterator();
            return new Iterator<T>() {
                public boolean hasNext() {
                    return superIter.hasNext();
                }

                public T next() {
                    return superIter.next();
                }

                public void remove() {
                    // ignore
                }
            };
        }

        public ListIterator<T> listIterator(final int index) {
            final ListIterator<T> superIter = super.listIterator(index);
            return new ListIterator<T>() {
                public boolean hasNext() {
                    return superIter.hasNext();
                }

                public T next() {
                    return superIter.next();
                }

                public boolean hasPrevious() {
                    return superIter.hasPrevious();
                }

                public T previous() {
                    return superIter.previous();
                }

                public int nextIndex() {
                    return superIter.nextIndex();
                }

                public int previousIndex() {
                    return superIter.previousIndex();
                }

                public void remove() {
                    // ignore
                }

                public void set(final T o) {
                    // ignore
                }

                public void add(final T o) {
                    // ignore
                }
            };
        }

        public boolean removeAll(final Collection<?> c) {
            // ignore
            return false;
        }

        public boolean retainAll(final Collection<?> c) {
            // ignore
            return false;
        }
    }

    private static final class ReadOnlyHashMap<K, V> extends HashMap<K, V> {

        private static final long serialVersionUID = -6048215349511680936L;

        ReadOnlyHashMap(final int size) {
            super(size);
        }

        static <K, V> ReadOnlyHashMap<K, V> of(final List<ReadOnlyMapEntry<K, V>> entries) {
            final ReadOnlyHashMap<K, V> result = new ReadOnlyHashMap<K, V>(entries.size());
            for (ReadOnlyMapEntry<K, V> entry : entries) {
                result.add(entry.getKey(), entry.getValue());
            }
            return result;
        }

        private void add(final K key, final V value) {
            super.put(key, value);
        }

        @Override
        public V put(final K key, final V value) {
            // ignore
            return null;
        }

        @Override
        public void putAll(final Map<? extends K, ? extends V> m) {
            // ignore
        }

        @Override
        public V remove(final Object key) {
            // ignore
            return null;
        }

        @Override
        public void clear() {
            // ignore
        }

        @Override
        public Collection<V> values() {
            return new ReadOnlyArrayList<V>(super.values());
        }
    }

    private static final class ReadOnlyMapEntry<K, V> implements Entry<K, V> {

        private final K key;
        private final V value;

        private ReadOnlyMapEntry(final K key, final V value) {
            this.key = key;
            this.value = value;
        }

        static <K, V> ReadOnlyMapEntry<K, V> of(final K key, final V value) {
            return new ReadOnlyMapEntry<K, V>(key, value);
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(final V value) {
            // ignore
            return null;
        }
    }

    private static class KnownLevelBuilder {
        private final Map<String, List<Object>> nameMap;
        private final Map<Integer, List<Object>> intMap;
        private final Constructor<?> constructor;

        private KnownLevelBuilder(final Constructor<?> constructor) {
            nameMap = new HashMap<String, List<Object>>();
            intMap = new HashMap<Integer, List<Object>>();
            this.constructor = constructor;
        }

        public KnownLevelBuilder add(final java.util.logging.Level level) throws IllegalAccessException, InvocationTargetException, InstantiationException {
            final String name = level.getName();
            final Object knownLevel = constructor.newInstance(level);
            List<Object> nl = nameMap.get(name);
            if (nl == null) {
                nl = new ArrayList<Object>();
                nameMap.put(name, nl);
            }
            nl.add(constructor.newInstance(knownLevel));

            final int intValue = level.intValue();
            List<Object> il = intMap.get(intValue);
            if (il == null) {
                il = new ArrayList<Object>();
                intMap.put(intValue, il);
            }
            il.add(knownLevel);
            return this;
        }

        public ReadOnlyHashMap<String, ReadOnlyArrayList<Object>> toNameMap() {
            final List<ReadOnlyMapEntry<String, ReadOnlyArrayList<Object>>> list =
                    new ArrayList<ReadOnlyMapEntry<String, ReadOnlyArrayList<Object>>>(nameMap.size());
            for (String key : nameMap.keySet()) {
                list.add(ReadOnlyMapEntry.of(key, ReadOnlyArrayList.of(nameMap.get(key))));
            }
            return ReadOnlyHashMap.of(list);
        }

        public ReadOnlyHashMap<Integer, ReadOnlyArrayList<Object>> toIntMap() {
            final List<ReadOnlyMapEntry<Integer, ReadOnlyArrayList<Object>>> list =
                    new ArrayList<ReadOnlyMapEntry<Integer, ReadOnlyArrayList<Object>>>(intMap.size());
            for (Integer key : intMap.keySet()) {
                list.add(ReadOnlyMapEntry.of(key, ReadOnlyArrayList.of(intMap.get(key))));
            }
            return ReadOnlyHashMap.of(list);
        }
    }

    // Configuration

    private final AtomicBoolean configured = new AtomicBoolean();

    private static String tryGetProperty(String name, String defaultVal) {
        try {
            return System.getProperty(name, defaultVal);
        } catch (Throwable t) {
            return defaultVal;
        }
    }

    /**
     * Configure the log manager one time.  An implementation of {@link ConfigurationLocator} is created by constructing an
     * instance of the class name specified in the {@code org.jboss.logmanager.configurationLocator} system property.
     */
    public void readConfiguration() throws IOException, SecurityException {
        checkAccess();
        if (configured.getAndSet(true)) {
            return;
        }
        final String confLocClassName = tryGetProperty("org.jboss.logmanager.configurationLocator", null);
        ConfigurationLocator locator = null;
        if (confLocClassName != null) {
            locator = construct(ConfigurationLocator.class, confLocClassName);
        } else {
            final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            if (tccl != null) {
                final ServiceLoader<ConfigurationLocator> loader = ServiceLoader.load(ConfigurationLocator.class, tccl);
                final Iterator<ConfigurationLocator> iterator = loader.iterator();
                if (iterator.hasNext()) {
                    locator = iterator.next();
                }
            }
            if (locator == null) {
                final ServiceLoader<ConfigurationLocator> loader = ServiceLoader.load(ConfigurationLocator.class, tccl != null ? tccl : LogManager.class.getClassLoader());
                final Iterator<ConfigurationLocator> iterator = loader.iterator();
                if (iterator.hasNext()) {
                    locator = iterator.next();
                } else {
                    locator = new DefaultConfigurationLocator();
                }
            }
        }
        if (locator != null) {
            final InputStream configuration = locator.findConfiguration();
            if (configuration != null) {
                readConfiguration(configuration);
            }
        }
    }

    /**
     * Configure the log manager.
     *
     * @param inputStream the input stream from which the logmanager should be configured
     */
    public void readConfiguration(InputStream inputStream) throws IOException, SecurityException {
        try {
            checkAccess();
            configured.set(true);
            final String confClassName = tryGetProperty("org.jboss.logmanager.configurator", null);
            Configurator configurator = null;
            if (confClassName != null) {
                configurator = construct(Configurator.class, confClassName);
            } else {
                final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                if (tccl != null) {
                    final ServiceLoader<Configurator> loader = ServiceLoader.load(Configurator.class, tccl);
                    final Iterator<Configurator> iterator = loader.iterator();
                    if (iterator.hasNext()) {
                        configurator = iterator.next();
                    }
                }
                if (configurator == null) {
                    final ServiceLoader<Configurator> loader = ServiceLoader.load(Configurator.class, LogManager.class.getClassLoader());
                    final Iterator<Configurator> iterator = loader.iterator();
                    if (iterator.hasNext()) {
                        configurator = iterator.next();
                    } else {
                        configurator = new PropertyConfigurator();
                    }
                }
            }
            if (configurator != null) try {
                configurator.configure(inputStream);
                LogContext.getSystemLogContext().getLogger("").attach(Configurator.ATTACHMENT_KEY, configurator);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } finally {
            try {
                inputStream.close();
            } catch (Throwable ignored) {}
        }
    }

    static <T> T construct(Class<? extends T> type, String className) throws IOException {
        try {
            Class<?> clazz = null;
            try {
                final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                if (tccl != null) {
                    clazz = Class.forName(className, true, tccl);
                }
            } catch (ClassNotFoundException ignore) {
            }
            if (clazz == null) clazz = Class.forName(className, true, LogManager.class.getClassLoader());
            return type.cast(clazz.getConstructor().newInstance());
        } catch (Exception e) {
            final IOException ioe = new IOException("Unable to load configuration class " + className);
            ioe.initCause(e);
            throw ioe;
        }
    }

    /**
     * Do nothing.  Properties and their listeners are not supported.
     *
     * @param l ignored
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        // no operation - properties are never changed
    }

    /**
     * Do nothing.  Properties and their listeners are not supported.
     *
     * @param l ignored
     */
    public void removePropertyChangeListener(PropertyChangeListener l) {
        // no operation - properties are never changed
    }

    /**
     * Does nothing.  Properties are not supported.
     *
     * @param name ignored
     * @return {@code null}
     */
    public String getProperty(String name) {
        // no properties
        return null;
    }

    /**
     * Does nothing.  This method only causes trouble.
     */
    public void reset() {
        // no operation!
    }

    @Override
    public Enumeration<String> getLoggerNames() {
        return LogContext.getLogContext().getLoggerNames();
    }

    /**
     * Do nothing.  Loggers are only added/acquired via {@link #getLogger(String)}.
     *
     * @param logger ignored
     * @return {@code false}
     */
    public boolean addLogger(java.util.logging.Logger logger) {
        return false;
    }

    /**
     * Get or create a logger with the given name.
     *
     * @param name the logger name
     * @return the corresponding logger
     */
    public Logger getLogger(String name) {
        return LogContext.getLogContext().getLogger(name);
    }

    /**
     * Returns the currently set filter for this thread or {@code null} if one has not been set.
     * <p>
     * If the {@link #PER_THREAD_LOG_FILTER_KEY} is not set to {@code true} then {@code null} will always be returned.
     * </p>
     *
     * @return the level used as effective level by all loggers inside the current thread or {@code null} if no level was set
     */
    public static java.util.logging.Level getThreadLocalEffectiveLevel() {
        return PER_THREAD_LOG_FILTER ? LocalFilterHolder.LOCAL_FILTER.get() : null;
    }

    /**
     * Sets the filter on the thread for all loggers.
     * <p>
     * This feature only works if the {@link #PER_THREAD_LOG_FILTER} was set to {@code true}
     * </p>
     *
     * @param level used as effective level by all loggers used by the current thread.
     */
    public static void setThreadLocalEffectiveLevel(final java.util.logging.Level level) {
        if (PER_THREAD_LOG_FILTER) {
            LocalFilterHolder.LOCAL_FILTER.set(level);
        }
    }
}
