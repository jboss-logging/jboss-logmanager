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

package org.jboss.logmanager.formatters;

import org.jboss.logmanager.ExtLogRecord;
import static java.lang.Math.max;

/**
 * A formatter which formats a record in a series of steps.
 */
public final class MultistepFormatter extends ExtFormatter {
    private final FormatStep[] steps;
    private final int builderLength;

    /**
     * Construct a new instance.
     *
     * @param steps the steps to execute to format the record
     */
    public MultistepFormatter(final FormatStep[] steps) {
        this.steps = steps;
        int builderLength = 0;
        for (FormatStep step : steps) {
            builderLength += step.estimateLength();
        }
        this.builderLength = max(32, builderLength);
    }

    /** {@inheritDoc} */
    public String format(final ExtLogRecord record) {
        final StringBuilder builder = new StringBuilder(builderLength);
        for (FormatStep step : steps) {
            step.render(builder, record);
        }
        return builder.toString();
    }
}
