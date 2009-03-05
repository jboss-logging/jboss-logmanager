/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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

import java.util.Map;
import java.util.HashMap;

/**
 *
 */
public final class MDC {

    private MDC() {}

    private static final Holder mdc = new Holder();

    public static String get(String key) {
        return mdc.get().get(key);
    }

    public static String put(String key, String value) {
        return mdc.get().put(key, value);
    }

    public static String remove(String key) {
        return mdc.get().remove(key);
    }

    public static Map<String, String> copy() {
        return new HashMap<String, String>(mdc.get());
    }

    private static final class Holder extends InheritableThreadLocal<Map<String, String>> {

        protected Map<String, String> childValue(final Map<String, String> parentValue) {
            return new HashMap<String, String>(parentValue);
        }

        protected Map<String, String> initialValue() {
            return new HashMap<String, String>();
        }
    }
}
