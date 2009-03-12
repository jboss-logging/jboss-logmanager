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
import org.slf4j.spi.MDCAdapter;

public final class Slf4jMDCAdapter implements MDCAdapter {

    public void put(final String key, final String val) {
        MDC.put(key, val);
    }

    public String get(final String key) {
        return MDC.get(key);
    }

    public void remove(final String key) {
        MDC.remove(key);
    }

    public void clear() {
        MDC.clear();
    }

    public Map getCopyOfContextMap() {
        return MDC.copy();
    }

    public void setContextMap(final Map contextMap) {
        MDC.clear();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) contextMap).entrySet()) {
            final Object key = entry.getKey();
            final Object value = entry.getValue();
            if (key != null && value != null) {
                MDC.put(key.toString(), value.toString());
            }
        }
    }
}
