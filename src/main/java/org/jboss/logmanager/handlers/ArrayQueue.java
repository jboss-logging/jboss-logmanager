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

package org.jboss.logmanager.handlers;

import java.util.Queue;
import java.util.Iterator;
import java.util.AbstractQueue;

final class ArrayQueue<T> extends AbstractQueue<T> implements Queue<T> {
    private final T[] elements;
    private int head, cnt;

    @SuppressWarnings({ "unchecked" })
    public ArrayQueue(int size) {
        elements = (T[]) new Object[size];
    }

    public Iterator<T> iterator() {
        throw new UnsupportedOperationException();
    }

    public int size() {
        return cnt;
    }

    public boolean offer(final T o) {
        final T[] elements = this.elements;
        final int length = elements.length;
        final int cnt = this.cnt;
        if (cnt == length) {
            return false;
        }
        elements[(head + cnt) % length] = o;
        this.cnt = cnt + 1;
        return true;
    }

    public T poll() {
        final T[] elements = this.elements;
        final int length = elements.length;
        final int head = this.head;
        final int cnt = this.cnt;
        if (cnt == 0) {
            return null;
        } else try {
            return elements[head];
        } finally {
            this.head = head + 1 % length;
            this.cnt = cnt - 1;
        }
    }

    public T peek() {
        return cnt == 0 ? null : elements[head];
    }
}
