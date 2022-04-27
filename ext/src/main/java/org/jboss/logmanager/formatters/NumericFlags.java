/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.formatters;

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
