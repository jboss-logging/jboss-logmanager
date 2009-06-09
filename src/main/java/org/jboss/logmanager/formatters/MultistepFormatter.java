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
public class MultistepFormatter extends ExtFormatter {
    private volatile FormatStep[] steps;
    private volatile int builderLength;

    private static final FormatStep[] EMPTY_STEPS = new FormatStep[0];

    /**
     * Construct a new instance.
     *
     * @param steps the steps to execute to format the record
     */
    public MultistepFormatter(final FormatStep[] steps) {
        this.steps = steps.clone();
        calculateBuilderLength();
    }

    private void calculateBuilderLength() {
        int builderLength = 0;
        for (FormatStep step : steps) {
            builderLength += step.estimateLength();
        }
        this.builderLength = max(32, builderLength);
    }

    /**
     * Construct a new instance.
     */
    public MultistepFormatter() {
        steps = EMPTY_STEPS;
    }

    /**
     * Get a copy of the format steps.
     *
     * @return a copy of the format steps
     */
    public FormatStep[] getSteps() {
        return steps.clone();
    }

    /**
     * Assign new format steps.
     *
     * @param steps the new format steps
     */
    public void setSteps(final FormatStep[] steps) {
        this.steps = steps == null || steps.length == 0 ? EMPTY_STEPS : steps.clone();
        calculateBuilderLength();
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
