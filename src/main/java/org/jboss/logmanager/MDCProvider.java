package org.jboss.logmanager;

import java.util.Map;

public interface MDCProvider {

    /**
     * Get the value for a key, or {@code null} if there is no mapping.
     *
     * @param key the key
     * @return the value
     */
    String get(String key);

    /**
     * Get the value for a key, or {@code null} if there is no mapping.
     *
     * @param key the key
     * @return the value
     */
    Object getObject(String key);

    /**
     * Set the value of a key, returning the old value (if any) or {@code null} if there was none.
     *
     * @param key   the key
     * @param value the new value
     * @return the old value or {@code null} if there was none
     */
    String put(String key, String value);

    /**
     * Set the value of a key, returning the old value (if any) or {@code null} if there was none.
     *
     * @param key   the key
     * @param value the new value
     * @return the old value or {@code null} if there was none
     */
    Object putObject(String key, Object value);

    /**
     * Remove a key.
     *
     * @param key the key
     * @return the old value or {@code null} if there was none
     */
    String remove(String key);

    /**
     * Remove a key.
     *
     * @param key the key
     * @return the old value or {@code null} if there was none
     */
    Object removeObject(String key);

    /**
     * Get a copy of the MDC map. This is a relatively expensive operation.
     *
     * @return a copy of the map
     */
    Map<String, String> copy();

    /**
     * Get a copy of the MDC map. This is a relatively expensive operation.
     *
     * @return a copy of the map
     */
    Map<String, Object> copyObject();

    /**
     * Returns {@code true} if the backing MDC map is empty, otherwise {@code false}.
     *
     * @return {@code true} if the MDC map is empty
     */
    boolean isEmpty();

    /**
     * Clear the current MDC map.
     */
    void clear();

}
