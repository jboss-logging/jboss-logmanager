package org.jboss.logmanager.ext.formatters;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.IntFunction;

abstract class FlagSet<E extends Enum<E>> extends AbstractSet<E> {
    final int value;

    FlagSet(final int value) {
        this.value = value;
    }

    abstract E[] values();

    public Iterator<E> iterator() {
        return new Iterator<E>() {
            int bits = value;

            public boolean hasNext() {
                return bits != 0;
            }

            public E next() {
                if (! hasNext()) throw new NoSuchElementException();
                int lob = Integer.lowestOneBit(bits);
                bits &= ~lob;
                return values()[Integer.numberOfTrailingZeros(lob)];
            }
        };
    }

    public int size() {
        return Integer.bitCount(value);
    }

    public void forEach(final Consumer<? super E> action) {
        int bits = value;
        int lob;
        while (bits != 0) {
            lob = Integer.lowestOneBit(bits);
            bits &= ~lob;
            action.accept(values()[Integer.numberOfTrailingZeros(lob)]);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T[] toArray(final IntFunction<T[]> generator) {
        T[] array = generator.apply(size());
        int idx = 0, lob, bits = value;
        while (bits != 0) {
            lob = Integer.lowestOneBit(bits);
            bits &= ~lob;
            array[idx++] = (T) values()[Integer.numberOfTrailingZeros(lob)];
        }
        return array;
    }

    public boolean contains(final Object o) {
        return o instanceof Enum<?> && contains((Enum<?>) o);
    }

    public boolean contains(final Enum<?> e) {
        // override in subclass for type verification
        return e != null && (value & 1 << e.ordinal()) != 0;
    }

    public int hashCode() {
        int hc = 0;
        int bits = value;
        int lob;
        while (bits != 0) {
            lob = Integer.lowestOneBit(bits);
            bits &= ~lob;
            hc += values()[Integer.numberOfTrailingZeros(lob)].hashCode();
        }
        return hc;
    }

    public boolean equals(final Object o) {
        return o.getClass() == getClass() && ((FlagSet<?>)o).value == value || super.equals(o);
    }

    public void forbid(final E flag) {
        if (contains(flag)) {
            throw notAllowed(flag);
        }
    }

    public void forbidAll() {
        if (! isEmpty()) {
            throw notAllowed(this);
        }
    }

    public void forbidAllBut(final E flag) {
        without(flag).forbidAll();
    }

    abstract FlagSet<E> without(final E flag);

    private static IllegalArgumentException notAllowed(final FlagSet<?> set) {
        return new IllegalArgumentException("Flags " + set + " are not allowed here");
    }

    private static IllegalArgumentException notAllowed(final Enum<?> flag) {
        return new IllegalArgumentException("Flag " + flag + " is not allowed here");
    }
}
