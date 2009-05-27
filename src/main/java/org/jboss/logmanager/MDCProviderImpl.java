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
import org.jboss.logging.MDCProvider;

final class MDCProviderImpl implements MDCProvider {

    public void put(final String key, final Object value) {
        MDC.put(key, String.valueOf(value));
    }

    public Object get(final String key) {
        return MDC.get(key);
    }

    public void remove(final String key) {
        MDC.remove(key);
    }

    @SuppressWarnings({ "unchecked" })
    public Map<String, Object> getMap() {
        // we can re-define the erasure of this map because MDC does not make further use of the copy
        return (Map)MDC.copy();
    }

    private static final MDCProvider instance = new MDCProviderImpl();

    public static MDCProvider getInstance() {
        return instance;
    }
}
