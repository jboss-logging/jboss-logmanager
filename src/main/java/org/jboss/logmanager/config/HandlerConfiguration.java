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

/**
 * Configuration for a single handler.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface HandlerConfiguration extends HandlerContainingConfigurable, NamedConfigurable, PropertyConfigurable, ObjectConfigurable {

    /**
     * Get the name of the configured formatter for this handler.
     *
     * @return the formatter name
     */
    String getFormatterName();

    /**
     * Set the name of the configured formatter for this handler.
     *
     * @param name the formatter name
     */
    void setFormatterName(String name);

    String getLevel();

    void setLevel(String level);

    String getFilter();

    void setFilter(String name);

    String getEncoding();

    void setEncoding(String name);

    String getErrorManagerName();

    void setErrorManagerName(String name);
}
