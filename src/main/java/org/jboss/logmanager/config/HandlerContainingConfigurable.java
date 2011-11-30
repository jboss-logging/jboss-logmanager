/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat Inc., and individual contributors
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

package org.jboss.logmanager.config;

import java.util.Collection;
import java.util.List;

/**
 * A configurable object which is a container for handlers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface HandlerContainingConfigurable {

    /**
     * Get the names of the configured handlers.
     *
     * @return the names of the configured handlers
     */
    List<String> getHandlerNames();

    /**
     * Set the names of the configured handlers.
     *
     * @param names the names of the configured handlers
     */
    void setHandlerNames(String... names);

    /**
     * Set the names of the configured handlers.
     *
     * @param names the names of the configured handlers
     */
    void setHandlerNames(Collection<String> names);

    /**
     * Add a handler name to this logger.
     *
     * @param name the handler name
     * @return {@code true} if the name was not already set, {@code false} if it was
     */
    boolean addHandlerName(String name);

    /**
     * Remove a handler name from this logger.
     *
     * @param name the handler name
     * @return {@code true} if the name was removed, {@code false} if it was not present
     */
    boolean removeHandlerName(String name);

}
