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
 * Configuration for a single logger.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface LoggerConfiguration extends NamedConfigurable, HandlerContainingConfigurable {

    // TODO (jrp) better document ValueExpression and filter expressions

    /**
     * Get the name of the filter to use.
     *
     * @return the filter name
     */
    String getFilter();

    /**
     * Returns a filter that may be an expression.
     *
     * @return the filter
     */
    ValueExpression<String> getFilterValueExpression();

    /**
     * Set the name of the filter to use, or {@code null} to leave unconfigured.
     *
     * @param name the filter name
     */
    void setFilter(String name);

    /**
     * Sets the expression value and for the filter.
     * <p/>
     * This method will not parse the expression for the value and instead use the {@code value} parameter for the
     * filter on the logger.
     *
     * @param expression the expression
     * @param value      the value to set the filter to
     */
    void setFilter(String expression, String value);

    /**
     * Determine whether parent handlers will be used.
     *
     * @return the setting, or {@code null} to leave unconfigured
     */
    Boolean getUseParentHandlers();

    /**
     * Returns a filter that may be an expression.
     *
     * @return the setting, or {@code null} to leave unconfigured as a value expression
     */
    ValueExpression<Boolean> getUseParentHandlersValueExpression();

    /**
     * Set whether to use parent handlers.  A value of {@code null} indicates that the value should be left
     * unconfigured.
     *
     * @param value whether to use parent handlers
     */
    void setUseParentHandlers(Boolean value);

    /**
     * Set whether to use parent handlers.
     *
     * @param expression the expression value used to resolve the setting
     *
     * @see #setUseParentHandlers(Boolean)
     * @see ValueExpression
     */
    void setUseParentHandlers(String expression);

    /**
     * Set whether to use parent handlers.
     * <p/>
     * This method will not parse the expression for the value and instead use the {@code value} parameter for the
     * setting on the logger.
     *
     * @param expression the expression
     * @param value      the value to set the setting to
     *
     * @see #setUseParentHandlers(Boolean)
     * @see ValueExpression
     */
    void setUseParentHandlers(String expression, Boolean value);

    /**
     * Gets the level set on the logger.
     *
     * @return the level
     */
    String getLevel();

    /**
     * Returns the level that may be an expression.
     *
     * @return the level
     */
    ValueExpression<String> getLevelValueExpression();

    /**
     * Sets the level on the logger.
     *
     * @param level the level to set, may be an expression
     *
     * @see ValueExpression
     */
    void setLevel(String level);

    /**
     * Sets the expression value for the level.
     * <p/>
     * This method will not parse the expression for the value and instead use the {@code level} parameter for the
     * level on the logger.
     *
     * @param expression the expression used to resolve the level
     * @param level      the level to use
     *
     * @see #setLevel(String)
     * @see ValueExpression
     */
    void setLevel(String expression, String level);
}
