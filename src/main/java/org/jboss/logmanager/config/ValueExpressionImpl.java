/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ValueExpressionImpl<T> implements ValueExpression<T> {

    private final String expression;
    private final T resolvedValue;

    ValueExpressionImpl(final String expression, final T resolvedValue) {
        this.expression = expression;
        this.resolvedValue = resolvedValue;
    }

    @Override
    public T getResolvedValue() {
        return resolvedValue;
    }

    @Override
    public boolean isExpression() {
        return expression != null;
    }

    @Override
    public String getValue() {
        return expression == null ? (resolvedValue == null ? null : String.valueOf(resolvedValue)) : expression;
    }

    @Override
    public String toString() {
        return getValue();
    }
}
