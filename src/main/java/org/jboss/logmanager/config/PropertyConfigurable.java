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

import java.util.List;

/**
 * An object which is configurable via object properties.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface PropertyConfigurable {

    /**
     * Set a property value from a string.
     *
     * @param propertyName the property name
     * @param value the property value
     * @throws IllegalArgumentException if the given value is not acceptable for this property
     */
    void setPropertyValueString(String propertyName, String value) throws IllegalArgumentException;

    /**
     * Get the string property value with the given name.
     *
     * @param propertyName the property name
     * @return the property value string
     */
    String getPropertyValueString(String propertyName);

    /**
     * Determine whether the given property name is configured.
     *
     * @param propertyName the property name to test
     * @return {@code true} if the name is configured, {@code false} otherwise
     */
    boolean hasProperty(String propertyName);

    /**
     * Remove a configured property.  Does not affect the underlying configured value; just removes it from the
     * configuration.
     *
     * @param propertyName the property name
     * @return {@code true} if the property name was removed, {@code false} if it was not present
     */
    boolean removeProperty(String propertyName);

    /**
     * Get the names of the configured properties in order.
     *
     * @return the property names
     */
    List<String> getPropertyNames();

    /**
     * Determine whether the given property name is a constructor property.
     *
     * @param propertyName the name of the property to check.
     *
     * @return {@code true} if the property should be used as a construction property, otherwise {@code false}.
     */
    boolean hasConstructorProperty(String propertyName);

    /**
     * Returns a collection of the constructor properties.
     *
     * @return a collection of the constructor properties.
     */
    List<String> getConstructorProperties();

    /**
     * Adds a method name to be invoked after all properties have been set.
     *
     * @param methodName the name of the method
     *
     * @return {@code true} if the method was successfully added, otherwise {@code false}
     */
    boolean addPostConfigurationMethod(String methodName);

    /**
     * Returns a collection of the methods to be invoked after the properties have been set.
     *
     * @return a collection of method names or an empty list
     */
    List<String> getPostConfigurationMethods();

    /**
     * Sets the method names to be invoked after the properties have been set.
     *
     * @param methodNames the method names to invoke
     */
    void setPostConfigurationMethods(String... methodNames);

    /**
     * Sets the method names to be invoked after the properties have been set.
     *
     * @param methodNames the method names to invoke
     */
    void setPostConfigurationMethods(List<String> methodNames);

    /**
     * Removes the post configuration method.
     *
     * @param methodName the method to remove
     *
     * @return {@code true} if the method was removed, otherwise {@code false}
     */
    boolean removePostConfigurationMethod(String methodName);
}
