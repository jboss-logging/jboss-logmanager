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

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.TimeZone;

/**
 * A parser which can translate a log4j-style format string into a series of {@code FormatStep} instances.
 */
public final class FormatStringParser {

    /**
     * The regular expression for format strings.  Ain't regex grand?
     */
    private static final Pattern pattern = Pattern.compile(
                // greedily match all non-format characters
                "([^%]++)" +
                // match a format string...
                "|(?:%" +
                    // optional minimum width plus justify flag
                    "(?:(-)?(\\d+))?" +
                    // optional maximum width
                    "(?:\\.(\\d+))?" +
                    // the actual format character
                    "(.)" +
                    // an optional argument string
                    "(?:\\{([^}]*)\\})?" +
                // end format string
                ")"
    );

    private FormatStringParser() {
    }

    /**
     * Compile a format string into a series of format steps.
     *
     * @param formatString the format string
     * @return the format steps
     */
    public static FormatStep[] getSteps(final String formatString) {
        final long time = System.currentTimeMillis();
        final ArrayList<FormatStep> stepList = new ArrayList<FormatStep>();
        final Matcher matcher = pattern.matcher(formatString);
        TimeZone timeZone = TimeZone.getDefault();
        while (matcher.find()) {
            final String otherText = matcher.group(1);
            if (otherText != null) {
                stepList.add(Formatters.textFormatStep(otherText));
            } else {
                final String hypen = matcher.group(2);
                final String minWidthString = matcher.group(3);
                final String maxWidthString = matcher.group(4);
                final String formatCharString = matcher.group(5);
                final String argument = matcher.group(6);
                final int minimumWidth = minWidthString == null ? 0 : Integer.parseInt(minWidthString);
                final boolean leftJustify = hypen != null;
                final int maximumWidth = maxWidthString == null ? 0 : Integer.parseInt(maxWidthString);
                final char formatChar = formatCharString.charAt(0);
                switch (formatChar) {
                    case 'c': {
                        final int count = argument == null ? 0 : Integer.parseInt(argument);
                        stepList.add(Formatters.loggerNameFormatStep(leftJustify, minimumWidth, maximumWidth, count));
                        break;
                    }
                    case 'C': {
                        final int count = argument == null ? 0 : Integer.parseInt(argument);
                        stepList.add(Formatters.classNameFormatStep(leftJustify, minimumWidth, maximumWidth, count));
                        break;
                    }
                    case 'd': {
                        stepList.add(Formatters.dateFormatStep(timeZone, argument, leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 'e': {
                        stepList.add(Formatters.exceptionFormatStep(leftJustify, minimumWidth, maximumWidth, false));
                        break;
                    }
                    case 'E': {
                        stepList.add(Formatters.exceptionFormatStep(leftJustify, minimumWidth, maximumWidth, true));
                        break;
                    }
                    case 'F': {
                        stepList.add(Formatters.fileNameFormatStep(leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 'k': {
                        stepList.add(Formatters.resourceKeyFormatStep(leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 'l': {
                        stepList.add(Formatters.locationInformationFormatStep(leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 'L': {
                        stepList.add(Formatters.lineNumberFormatStep(leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 'm': {
                        stepList.add(Formatters.messageFormatStep(leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 'M': {
                        stepList.add(Formatters.methodNameFormatStep(leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 'n': {
                        stepList.add(Formatters.lineSeparatorFormatStep(leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 'p': {
                        stepList.add(Formatters.levelFormatStep(leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 'r': {
                        stepList.add(Formatters.relativeTimeFormatStep(time, leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 's': {
                        stepList.add(Formatters.simpleMessageFormatStep(leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 't': {
                        stepList.add(Formatters.threadNameFormatStep(leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 'x': {
                        final int count = argument == null ? 0 : Integer.parseInt(argument);
                        stepList.add(Formatters.ndcFormatStep(leftJustify, minimumWidth, maximumWidth, count));
                        break;
                    }
                    case 'X': {
                        stepList.add(Formatters.mdcFormatStep(argument, leftJustify, minimumWidth, maximumWidth));
                        break;
                    }
                    case 'z': {
                        timeZone = TimeZone.getTimeZone(argument);
                        break;
                    }
                    case '%': {
                        stepList.add(Formatters.textFormatStep("%"));
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException("Encountered an unknown format character");
                    }
                }
            }
        }
        return stepList.toArray(new FormatStep[stepList.size()]);
    }
}
