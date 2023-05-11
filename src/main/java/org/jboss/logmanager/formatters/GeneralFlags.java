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
 * A set of general format flags.
 */
final class GeneralFlags extends FlagSet<GeneralFlag> {
    static final GeneralFlag[] values = GeneralFlag.values();

    private static final AtomicReferenceArray<GeneralFlags> ALL_SETS = new AtomicReferenceArray<>(1 << values.length);

    public static final GeneralFlags NONE = getOrCreateSet(0);

    private GeneralFlags(final int value) {
        super(value);
    }

    GeneralFlag[] values() {
        return values;
    }

    public boolean contains(final Enum<?> e) {
        return e instanceof GeneralFlag && super.contains(e);
    }

    public static GeneralFlags of(GeneralFlag flag) {
        return flag == null ? NONE : getOrCreateSet(1 << flag.ordinal());
    }

    public static GeneralFlags of(GeneralFlag flag1, GeneralFlag flag2) {
        return of(flag1).with(flag2);
    }

    public static GeneralFlags of(GeneralFlag flag1, GeneralFlag flag2, GeneralFlag flag3) {
        return of(flag1).with(flag2).with(flag3);
    }

    public GeneralFlags with(final GeneralFlag flag) {
        return flag == null ? this : getOrCreateSet(value | 1 << flag.ordinal());
    }

    public GeneralFlags without(final GeneralFlag flag) {
        return flag == null ? this : getOrCreateSet(value & ~(1 << flag.ordinal()));
    }

    private static GeneralFlags getOrCreateSet(final int bits) {
        GeneralFlags set = ALL_SETS.get(bits);
        if (set == null) {
            set = new GeneralFlags(bits);
            if (!ALL_SETS.compareAndSet(bits, null, set)) {
                GeneralFlags appearing = ALL_SETS.get(bits);
                if (appearing != null) {
                    set = appearing;
                }
            }
        }
        return set;
    }
}
