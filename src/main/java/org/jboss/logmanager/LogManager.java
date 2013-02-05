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

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simplified log manager.  Designed to work around the (many) design flaws of the JDK platform log manager.
 */
public final class LogManager extends java.util.logging.LogManager {

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

    // Configuration

    private final AtomicBoolean configured = new AtomicBoolean();

    /**
     * Configure the log manager one time.  An implementation of {@link ConfigurationLocator} is created by constructing an
     * instance of the class name specified in the {@code org.jboss.logmanager.configurationLocator} system property.
     */
    public void readConfiguration() throws IOException, SecurityException {
        checkAccess();
        if (configured.getAndSet(true)) {
            return;
        }
        final String confLocClassName = System.getProperty("org.jboss.logmanager.configurationLocator", DefaultConfigurationLocator.class.getName());
        final ConfigurationLocator locator = construct(ConfigurationLocator.class, confLocClassName);
        final InputStream configuration = locator.findConfiguration();
        if (configuration != null) {
            readConfiguration(configuration);
        }
    }

    /**
     * Configure the log manager.
     *
     * @param inputStream the input stream from which the logmanager should be configured
     */
    public void readConfiguration(InputStream inputStream) throws IOException, SecurityException {
        checkAccess();
        configured.set(true);
        final String confClassName = System.getProperty("org.jboss.logmanager.configurator", PropertyConfigurator.class.getName());
        final Configurator configurator = construct(Configurator.class, confClassName);
        try {
            configurator.configure(inputStream);
            LogContext.getSystemLogContext().getLogger("").attach(Configurator.ATTACHMENT_KEY, configurator);
        } catch (Throwable t) {
            t.printStackTrace();
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

    /**
     * Does nothing.  Logger names are not available.
     *
     * @return an empty enumeration
     */
    public Enumeration<String> getLoggerNames() {
        return new Enumeration<String>() {
            public boolean hasMoreElements() {
                return false;
            }

            public String nextElement() {
                throw new NoSuchElementException("No elements");
            }
        };
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
}
