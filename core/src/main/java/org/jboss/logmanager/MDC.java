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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Mapped diagnostic context.  This is a thread-local map used to hold loggable information.
 */
public final class MDC {
    private static final MDCProvider mdcProvider = getDefaultMDCProvider();

    private MDC() {}

    static MDCProvider getMDCProvider() {
        return mdcProvider;
    }

    private static MDCProvider getDefaultMDCProvider() {
        return System.getSecurityManager() == null ? doGetDefaultMDCProvider() : AccessController.doPrivileged((PrivilegedAction<MDCProvider>) MDC::doGetDefaultMDCProvider);
    }

    private static MDCProvider doGetDefaultMDCProvider() {
        final ServiceLoader<MDCProvider> configLoader = ServiceLoader.load(MDCProvider.class, MDC.class.getClassLoader());
        final Iterator<MDCProvider> iterator = configLoader.iterator();
        for (;;) try {
            if (! iterator.hasNext()) {
                return new ThreadLocalMDC();
            }
            return iterator.next();
        } catch (ServiceConfigurationError | RuntimeException e) {
            System.err.print("Warning: failed to load MDC Provider: ");
            e.printStackTrace(System.err);
        }
    }

    /**
     * Get the value for a key, or {@code null} if there is no mapping.
     *
     * @param key the key
     * @return the value
     */
    public static String get(String key) {
        return mdcProvider.get(key);
    }

    /**
     * Get the value for a key, or {@code null} if there is no mapping.
     *
     * @param key the key
     * @return the value
     */
    public static Object getObject(String key) {
        return mdcProvider.getObject(key);
    }

    /**
     * Set the value of a key, returning the old value (if any) or {@code null} if there was none.
     *
     * @param key the key
     * @param value the new value
     * @return the old value or {@code null} if there was none
     */
    public static String put(String key, String value) {
        return mdcProvider.put(key, value);
    }

    /**
     * Set the value of a key, returning the old value (if any) or {@code null} if there was none.
     *
     * @param key the key
     * @param value the new value
     * @return the old value or {@code null} if there was none
     */
    public static Object putObject(String key, Object value) {
        return mdcProvider.putObject(key, value);
    }

    /**
     * Remove a key.
     *
     * @param key the key
     * @return the old value or {@code null} if there was none
     */
    public static String remove(String key) {
        return mdcProvider.remove(key);
    }

    /**
     * Remove a key.
     *
     * @param key the key
     * @return the old value or {@code null} if there was none
     */
    public static Object removeObject(String key) {
        return mdcProvider.removeObject(key);
    }

    /**
     * Get a copy of the MDC map.  This is a relatively expensive operation.
     *
     * @return a copy of the map
     */
    public static Map<String, String> copy() {
        return mdcProvider.copy();
    }

    /**
     * Get a copy of the MDC map.  This is a relatively expensive operation.
     *
     * @return a copy of the map
     */
    public static Map<String, Object> copyObject() {
        return mdcProvider.copyObject();
    }

    /**
     * Clear the current MDC map.
     */
    public static void clear() {
        mdcProvider.clear();
    }
}
