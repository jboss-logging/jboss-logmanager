/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.formatters;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Splitter {

    private static class SplitIterator implements Iterator<String> {

        private final String value;
        private final char delimiter;
        private int index;

        private SplitIterator(final String value, final char delimiter) {
            this.value = value;
            this.delimiter = delimiter;
            index = 0;
        }

        public static SplitIterator of(final String value, final char delimiter) {
            return new SplitIterator(value, delimiter);
        }

        @Override
        public boolean hasNext() {
            return index != -1;
        }

        @Override
        public String next() {
            final int index = this.index;
            if (index == -1) {
                throw new NoSuchElementException();
            }
            int x = value.indexOf(delimiter, index);
            try {
                return x == -1 ? value.substring(index) : value.substring(index, x);
            } finally {
                this.index = (x == -1 ? -1 : x + 1);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class SplitIterable implements Iterable<String> {

        private final String value;
        private final char delimiter;

        private SplitIterable(final String value, final char delimiter) {
            this.value = value;
            this.delimiter = delimiter;
        }

        @Override
        public Iterator<String> iterator() {
            return new SplitIterator(value, delimiter);
        }
    }

    public static Iterable<String> iterable(final String value, final char delimiter) {
        return new SplitIterable(value, delimiter);
    }

    public static Iterator<String> iterator(final String value, final char delimiter) {
        return new SplitIterator(value, delimiter);
    }
}
