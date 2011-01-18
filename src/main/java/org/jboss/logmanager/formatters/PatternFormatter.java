/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Inc., and individual contributors
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

/**
 * A formatter which uses a text pattern to format messages.
 */
public class PatternFormatter extends MultistepFormatter {

    private volatile String pattern;

    /**
     * Construct a new instance.
     */
    public PatternFormatter() {
    }

    /**
     * Construct a new instance.
     *
     * @param pattern the initial pattern
     */
    public PatternFormatter(String pattern) {
        super(FormatStringParser.getSteps(pattern));
        this.pattern = pattern;
    }

    /**
     * Get the current format pattern.
     *
     * @return the pattern
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Set the format pattern.
     *
     * @param pattern the pattern
     */
    public void setPattern(final String pattern) {
        setSteps(FormatStringParser.getSteps(pattern));
        this.pattern = pattern;
    }
}
