/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager.formatters;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.handlers.ConsoleHandler;

/**
 * A pattern formatter that colorizes the pattern in a fixed manner.
 */
public class ColorPatternFormatter extends PatternFormatter {

    private final Printf printf;
    private final boolean darken;

    public ColorPatternFormatter() {
        this(false);
    }

    public ColorPatternFormatter(final String pattern) {
        this(false, pattern);
    }

    public ColorPatternFormatter(int darken) {
        this(darken > 0);
    }

    public ColorPatternFormatter(boolean darken) {
        this.darken = darken;
        printf = new ColorPrintf(darken);
    }

    public ColorPatternFormatter(int darken, final String pattern) {
        this(darken > 0, pattern);
    }

    public ColorPatternFormatter(boolean darken, final String pattern) {
        this(darken);
        setPattern(pattern);
    }

    public void setSteps(final FormatStep[] steps) {
        FormatStep[] colorSteps = new FormatStep[steps.length];
        for (int i = 0; i < steps.length; i++) {
            colorSteps[i] = colorize(steps[i]);
        }
        super.setSteps(colorSteps);
    }

    private FormatStep colorize(final FormatStep step) {
        return switch (step.getItemType()) {
            case LEVEL -> new LevelColorStep(step, darken);
            case SOURCE_CLASS_NAME, SOURCE_METHOD_NAME, SOURCE_LINE_NUMBER, SOURCE_FILE_NAME ->
                new ColorStep(step, 60f, 1f, .8f, darken);
            case DATE, RELATIVE_TIME, TEXT, MESSAGE ->
                new ColorStep(step, 0, 0, .8f, darken);
            case HOST_NAME, RESOURCE_KEY, SOURCE_MODULE_VERSION ->
                new ColorStep(step, 120f, 1f, .8f, darken);
            case LINE_SEPARATOR -> step;
            case CATEGORY ->
                new ColorStep(step, 220f, .9f, .8f, darken);
            case MDC, NDC ->
                new ColorStep(step, 153f, 1f, .7f, darken);
            case EXCEPTION_TRACE ->
                new ColorStep(step, 0, 1f, .6f, darken);
            case SOURCE_MODULE_NAME ->
                new ColorStep(step, 100f, 1f, .8f, darken);
            case PROCESS_ID ->
                new ColorStep(step, 40f, 0.6f, .7f, darken);
            case PROCESS_NAME ->
                new ColorStep(step, 60f, 0.6f, .7f, darken);
            case SYSTEM_PROPERTY ->
                new ColorStep(step, 60f, 1f, .6f, darken);
            case THREAD_ID, THREAD_NAME ->
                new ColorStep(step, 120f, 0.429f, .8f, darken);
            default -> new ColorStep(step, 120f, .254f, .8f, darken);
        };
    }

    private String colorizePlain(final String str) {
        return str;
    }

    public String formatMessage(final LogRecord logRecord) {
        if (logRecord instanceof ExtLogRecord record) {
            if (record.getFormatStyle() != ExtLogRecord.FormatStyle.PRINTF || record.getParameters() == null
                    || record.getParameters().length == 0) {
                return colorizePlain(super.formatMessage(record));
            }
            return printf.format(record.getMessage(), record.getParameters());
        } else {
            return colorizePlain(super.formatMessage(logRecord));
        }
    }

    static final class ColorStep implements FormatStep {
        private final FormatStep delegate;
        private final float hue;
        private final float sat;
        private final float lite;
        private final boolean darken;
        // capture current console state
        private final boolean trueColor = ConsoleHandler.isTrueColor();

        ColorStep(final FormatStep delegate, final float hue, final float sat, final float lite, final boolean darken) {
            this.delegate = delegate;
            this.hue = hue;
            this.sat = sat;
            this.lite = lite;
            this.darken = darken;
        }

        public void render(final Formatter formatter, final StringBuilder builder, final ExtLogRecord record) {
            ColorUtil.startFgColor(builder, trueColor, hue, sat, lite, darken);
            delegate.render(formatter, builder, record);
            ColorUtil.endFgColor(builder);
        }

        public void render(final StringBuilder builder, final ExtLogRecord record) {
            render(null, builder, record);
        }

        public int estimateLength() {
            return delegate.estimateLength() + 30;
        }

        public boolean isCallerInformationRequired() {
            return delegate.isCallerInformationRequired();
        }

        public ItemType getItemType() {
            return delegate.getItemType();
        }
    }

    static final class LevelColorStep implements FormatStep {
        private static final int LARGEST_LEVEL = Level.ERROR.intValue();
        private static final int SMALLEST_LEVEL = Level.FINEST.intValue();
        private final FormatStep delegate;
        private final boolean darken;
        // capture current console state
        private final boolean trueColor = ConsoleHandler.isTrueColor();

        LevelColorStep(final FormatStep delegate, final boolean darken) {
            this.delegate = delegate;
            this.darken = darken;
        }

        public void render(final Formatter formatter, final StringBuilder builder, final ExtLogRecord record) {
            // clip level at the "ends" of their reasonable ranges
            // we also have to swap the "direction" so that lower severity has a higher hue
            final int level = LARGEST_LEVEL - Math.max(Math.min(record.getLevel().intValue(), LARGEST_LEVEL), SMALLEST_LEVEL);
            // hue is periodic in the range [0..360°), but we want to stop somewhere in the purple-ish range, so [0..270°]
            final float hue = ((float) level) * 270f / ((float) LARGEST_LEVEL - (float) SMALLEST_LEVEL);
            // saturation/chroma is locked here for levels (we could make this configurable but this looks pretty good)
            final float sat = 1f;
            // lightness depends on "darken"
            final float lite = 0.75f;
            ColorUtil.startFgColor(builder, trueColor, hue, sat, lite, darken);
            delegate.render(formatter, builder, record);
            ColorUtil.endFgColor(builder);
        }

        public void render(final StringBuilder builder, final ExtLogRecord record) {
            render(null, builder, record);
        }

        public int estimateLength() {
            return delegate.estimateLength() + 30;
        }
    }
}
