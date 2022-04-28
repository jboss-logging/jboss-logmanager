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

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import static java.lang.Math.abs;

/**
 * A pattern formatter that colorizes the pattern in a fixed manner.
 */
public class ColorPatternFormatter extends PatternFormatter {

    private final Printf printf;
    private final int darken;

    public ColorPatternFormatter() {
        this(0);
    }

    public ColorPatternFormatter(final String pattern) {
        this(0, pattern);
    }

    public ColorPatternFormatter(int darken) {
        this.darken = darken;
        printf = new ColorPrintf(darken);
    }

    public ColorPatternFormatter(int darken, final String pattern) {
        this(darken);
        setPattern(pattern);
    }

    static final boolean trueColor = determineTrueColor();

    static boolean determineTrueColor() {
        final String colorterm = System.getenv("COLORTERM");
        return (colorterm != null && (colorterm.contains("truecolor") || colorterm.contains("24bit")));
    }

    static boolean isTrueColor() {
        return trueColor;
    }

    public void setSteps(final FormatStep[] steps) {
        FormatStep[] colorSteps = new FormatStep[steps.length];
        for (int i = 0; i < steps.length; i++) {
            colorSteps[i] = colorize(steps[i]);
        }
        super.setSteps(colorSteps);
    }

    private FormatStep colorize(final FormatStep step) {
        switch (step.getItemType()) {
            case LEVEL:
                return new LevelColorStep(step, darken);
            case SOURCE_CLASS_NAME:
                return new ColorStep(step, 0xff, 0xff, 0x44, darken);
            case DATE:
                return new ColorStep(step, 0xc0, 0xc0, 0xc0, darken);
            case SOURCE_FILE_NAME:
                return new ColorStep(step, 0xff, 0xff, 0x44, darken);
            case HOST_NAME:
                return new ColorStep(step, 0x44, 0xff, 0x44, darken);
            case SOURCE_LINE_NUMBER:
                return new ColorStep(step, 0xff, 0xff, 0x44, darken);
            case LINE_SEPARATOR:
                return step;
            case CATEGORY:
                return new ColorStep(step, 0x44, 0x88, 0xff, darken);
            case MDC:
                return new ColorStep(step, 0x44, 0xff, 0xaa, darken);
            case MESSAGE:
                return new ColorStep(step, 0xff, 0xff, 0xff, darken);
            case EXCEPTION_TRACE:
                return new ColorStep(step, 0xff, 0x44, 0x44, darken);
            case SOURCE_METHOD_NAME:
                return new ColorStep(step, 0xff, 0xff, 0x44, darken);
            case SOURCE_MODULE_NAME:
                return new ColorStep(step, 0x88, 0xff, 0x44, darken);
            case SOURCE_MODULE_VERSION:
                return new ColorStep(step, 0x44, 0xff, 0x44, darken);
            case NDC:
                return new ColorStep(step, 0x44, 0xff, 0xaa, darken);
            case PROCESS_ID:
                return new ColorStep(step, 0xdd, 0xbb, 0x77, darken);
            case PROCESS_NAME:
                return new ColorStep(step, 0xdd, 0xdd, 0x77, darken);
            case RELATIVE_TIME:
                return new ColorStep(step, 0xc0, 0xc0, 0xc0, darken);
            case RESOURCE_KEY:
                return new ColorStep(step, 0x44, 0xff, 0x44, darken);
            case SYSTEM_PROPERTY:
                return new ColorStep(step, 0x88, 0x88, 0x00, darken);
            case TEXT:
                return new ColorStep(step, 0xd0, 0xd0, 0xd0, darken);
            case THREAD_ID:
                return new ColorStep(step, 0x44, 0xaa, 0x44, darken);
            case THREAD_NAME:
                return new ColorStep(step, 0x44, 0xaa, 0x44, darken);
            case COMPOUND:
            case GENERIC:
            default:
                return new ColorStep(step, 0xb0, 0xd0, 0xb0, darken);
        }
    }

    private String colorizePlain(final String str) {
        return str;
    }

    public String formatMessage(final LogRecord logRecord) {
        if (logRecord instanceof ExtLogRecord) {
            final ExtLogRecord record = (ExtLogRecord) logRecord;
            if (record.getFormatStyle() != ExtLogRecord.FormatStyle.PRINTF || record.getParameters() == null || record.getParameters().length == 0) {
                return colorizePlain(super.formatMessage(record));
            }
            return printf.format(record.getMessage(), record.getParameters());
        } else {
            return colorizePlain(super.formatMessage(logRecord));
        }
    }

    static final class ColorStep implements FormatStep {
        private final int r, g, b;
        private final FormatStep delegate;

        ColorStep(final FormatStep delegate, final int r, final int g, final int b, final int darken) {
            this.r = r >>> darken;
            this.g = g >>> darken;
            this.b = b >>> darken;
            this.delegate = delegate;
        }

        public void render(final Formatter formatter, final StringBuilder builder, final ExtLogRecord record) {
            ColorUtil.startFgColor(builder, isTrueColor(), r, g, b);
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
        private static final int SMALLEST_LEVEL = Level.TRACE.intValue();
        private static final int SATURATION = 66;
        private final FormatStep delegate;
        private final int darken;

        LevelColorStep(final FormatStep delegate, final int darken) {
            this.delegate = delegate;
            this.darken = darken;
        }

        public void render(final Formatter formatter, final StringBuilder builder, final ExtLogRecord record) {
            final int level = Math.max(Math.min(record.getLevel().intValue(), LARGEST_LEVEL), SMALLEST_LEVEL) - SMALLEST_LEVEL;
            // really crappy linear interpolation
            int r = ((level < 300 ? 0 : (level - 300) * (255 - SATURATION) / 300) + SATURATION) >>> darken;
            int g = ((300 - abs(level - 300)) * (255 - SATURATION) / 300 + SATURATION) >>> darken;
            int b = ((level > 300 ? 0 : level * (255 - SATURATION) / 300) + SATURATION) >>> darken;
            ColorUtil.startFgColor(builder, isTrueColor(), r, g, b);
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
            return false;
        }
    }

    static final class TrueColorHolder {
        private TrueColorHolder() {}

        static final boolean trueColor = ColorPatternFormatter.determineTrueColor();
    }
}
