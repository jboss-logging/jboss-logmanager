package org.jboss.logmanager.ext.formatters;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A set of numeric flags.
 */
final class NumericFlags extends FlagSet<NumericFlag> {
    static final NumericFlag[] values = NumericFlag.values();

    private static final AtomicReferenceArray<NumericFlags> ALL_SETS = new AtomicReferenceArray<>(1 << values.length);

    public static final NumericFlags NONE = getOrCreateSet(0);

    private NumericFlags(final int value) {
        super(value);
    }

    NumericFlag[] values() {
        return values;
    }

    public boolean contains(final Enum<?> e) {
        return e instanceof NumericFlag && super.contains(e);
    }

    public static NumericFlags of(NumericFlag flag) {
        return flag == null ? NONE : getOrCreateSet(1 << flag.ordinal());
    }

    public static NumericFlags of(NumericFlag flag1, NumericFlag flag2) {
        return of(flag1).with(flag2);
    }

    public static NumericFlags of(NumericFlag flag1, NumericFlag flag2, NumericFlag flag3) {
        return of(flag1).with(flag2).with(flag3);
    }

    public NumericFlags with(final NumericFlag flag) {
        return flag == null ? this : getOrCreateSet(value | 1 << flag.ordinal());
    }

    public NumericFlags without(final NumericFlag flag) {
        return flag == null ? this : getOrCreateSet(value & ~(1 << flag.ordinal()));
    }

    private static NumericFlags getOrCreateSet(final int bits) {
        NumericFlags set = ALL_SETS.get(bits);
        if (set == null) {
            set = new NumericFlags(bits);
            if (! ALL_SETS.compareAndSet(bits, null, set)) {
                NumericFlags appearing = ALL_SETS.get(bits);
                if (appearing != null) {
                    set = appearing;
                }
            }
        }
        return set;
    }
}
