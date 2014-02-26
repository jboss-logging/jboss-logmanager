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

    private volatile ColorMap colors;

    private volatile FormatStringParser parser;

    /**
     * Construct a new instance.
     */
    public PatternFormatter() {
        this.colors = ColorMap.DEFAULT_COLOR_MAP;
        this.parser = new FormatStringParser();
    }

    /**
     * Construct a new instance.
     *
     * @param pattern the initial pattern
     */
    public PatternFormatter(String pattern) {
        this.colors = ColorMap.DEFAULT_COLOR_MAP;
        this.pattern = pattern;
        this.parser = new FormatStringParser();
        setSteps(parser.getSteps(pattern, colors));
    }

    /**
     * Construct a new instance.
     *
     * @param pattern the initial pattern
     * @param colors the color map to use
     */
    public PatternFormatter(String pattern, String colors) {
        ColorMap colorMap = ColorMap.create(colors);
        this.colors = colorMap;
        this.pattern = pattern;
        setSteps(parser.getSteps(pattern, colorMap));
    }

    public PatternFormatter(String pattern, String colors, FormatStringParser parser) {
        if (colors != null) {
            ColorMap colorMap = ColorMap.create(colors);
            this.colors = colorMap;
        } else {
            this.colors = ColorMap.DEFAULT_COLOR_MAP;
        }
        this.pattern = pattern;
        this.parser = parser;
        setSteps(parser.getSteps(pattern, this.colors));
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
        setSteps(parser.getSteps(pattern, colors));
        this.pattern = pattern;
    }

    /**
     * Set the color map to use for log levels when %K{level} is used.
     *
     * <p>The format is level:color,level:color,...
     *
     * <p>Where level is either a numerical value or one of the following constants:</p>
     *
     * <table>
     *     <tr><td>fatal</td></tr>
     *     <tr><td>error</td></tr>
     *     <tr><td>severe</td></tr>
     *     <tr><td>warn</td></tr>
     *     <tr><td>warning</td></tr>
     *     <tr><td>info</td></tr>
     *     <tr><td>config</td></tr>
     *     <tr><td>debug</td></tr>
     *     <tr><td>trace</td></tr>
     *     <tr><td>fine</td></tr>
     *     <tr><td>finer</td></tr>
     *     <tr><td>finest</td></tr>
     * </table>
     *
     * <p>Color is one of the following constants:</p>
     *
     * <table>
     *     <tr><td>clear</td></tr>
     *     <tr><td>black</td></tr>
     *     <tr><td>red</td></tr>
     *     <tr><td>green</td></tr>
     *     <tr><td>yellow</td></tr>
     *     <tr><td>blue</td></tr>
     *     <tr><td>magenta</td></tr>
     *     <tr><td>cyan</td></tr>
     *     <tr><td>white</td></tr>
     *     <tr><td>brightblack</td></tr>
     *     <tr><td>brightred</td></tr>
     *     <tr><td>brightgreen</td></tr>
     *     <tr><td>brightyellow</td></tr>
     *     <tr><td>brightblue</td></tr>
     *     <tr><td>brightmagenta</td></tr>
     *     <tr><td>brightcyan</td></tr>
     *     <tr><td>brightwhite</td></tr>
     * </table>
     *
     * @param colors a colormap expression string described above
     */
    public void setColors(String colors) {
        ColorMap colorMap = ColorMap.create(colors);
        this.colors = colorMap;
        if (pattern != null) {
            setSteps(parser.getSteps(pattern, colorMap));
        }
    }

    public String getColors() {
        return this.colors.toString();
    }
    
    public FormatStringParser getParser() {
        return this.parser;
    }
    
    public void setParser(FormatStringParser parser) {
        this.parser = parser;
        if (pattern != null) {
            setSteps(parser.getSteps(pattern, colors));
        }
    }
}
