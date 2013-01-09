/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat Inc., and individual contributors
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

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

final class CopyOnWriteMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, Cloneable {

    private static final FastCopyHashMap EMPTY = new FastCopyHashMap(32, 0.25f);

    @SuppressWarnings("unchecked")
    private volatile FastCopyHashMap<K, V> map = EMPTY;

    private static final AtomicReferenceFieldUpdater<CopyOnWriteMap, FastCopyHashMap> mapUpdater = AtomicReferenceFieldUpdater.newUpdater(CopyOnWriteMap.class, FastCopyHashMap.class, "map");

    public V get(final Object key) {
        return map.get(key);
    }

    public boolean containsKey(final Object key) {
        return map.containsKey(key);
    }

    public int size() {
        return map.size();
    }

    public boolean containsValue(final Object value) {
        return map.containsValue(value);
    }

    @SuppressWarnings("unchecked")
    public void clear() {
        map = EMPTY;
    }

    public V put(final K key, final V value) {
        FastCopyHashMap<K, V> oldVal, newVal;
        V result;
        do {
            oldVal = map;
            newVal = oldVal.clone();
            result = newVal.put(key, value);
        } while (! mapUpdater.compareAndSet(this, oldVal, newVal));
        return result;
    }

    public V remove(final Object key) {
        FastCopyHashMap<K, V> oldVal, newVal;
        V result;
        do {
            oldVal = map;
            if (! oldVal.containsKey(key)) {
                return null;
            }
            newVal = oldVal.clone();
            result = newVal.remove(key);
        } while (! mapUpdater.compareAndSet(this, oldVal, newVal));
        return result;
    }

    @SuppressWarnings("unchecked")
    public CopyOnWriteMap<K, V> clone() {
        try {
            return (CopyOnWriteMap<K, V>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException();
        }
    }

    public V putIfAbsent(final K key, final V value) {
        FastCopyHashMap<K, V> oldVal, newVal;
        do {
            oldVal = map;
            if (oldVal.containsKey(key)) {
                return oldVal.get(key);
            }
            newVal = oldVal.clone();
            newVal.put(key, value);
        } while (! mapUpdater.compareAndSet(this, oldVal, newVal));
        return null;
    }

    @SuppressWarnings("unchecked")
    public boolean remove(final Object key, final Object value) {
        FastCopyHashMap<K, V> oldVal, newVal;
        do {
            oldVal = map;
            if (value == oldVal.get(key) && (value != null || oldVal.containsKey(key))) {
                if (oldVal.size() == 1) {
                    newVal = EMPTY;
                } else {
                    newVal = oldVal.clone();
                    newVal.remove(key);
                }
            } else {
                return false;
            }
        } while (! mapUpdater.compareAndSet(this, oldVal, newVal));
        return true;
    }

    public boolean replace(final K key, final V oldValue, final V newValue) {
        FastCopyHashMap<K, V> oldVal, newVal;
        do {
            oldVal = map;
            if (oldValue == oldVal.get(key) && (oldValue != null || oldVal.containsKey(key))) {
                newVal = oldVal.clone();
                newVal.put(key, newValue);
            } else {
                return false;
            }
        } while (! mapUpdater.compareAndSet(this, oldVal, newVal));
        return true;
    }

    public V replace(final K key, final V value) {
        FastCopyHashMap<K, V> oldVal, newVal;
        V result;
        do {
            oldVal = map;
            if (value == oldVal.get(key) && (value != null || oldVal.containsKey(key))) {
                newVal = oldVal.clone();
                result = newVal.put(key, value);
            } else {
                return null;
            }
        } while (! mapUpdater.compareAndSet(this, oldVal, newVal));
        return result;
    }

    public Set<K> keySet() {
        return Collections.unmodifiableSet(map.keySet());
    }

    public Collection<V> values() {
        return Collections.unmodifiableCollection(map.values());
    }

    public Set<Entry<K, V>> entrySet() {
        return Collections.unmodifiableSet(map.entrySet());
    }
}
