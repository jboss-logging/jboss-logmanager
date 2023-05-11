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

import static java.lang.Math.max;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.AttributedCharacterIterator;
import java.text.DateFormatSymbols;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalUnit;
import java.time.temporal.ValueRange;
import java.util.Calendar;
import java.util.Date;
import java.util.Formattable;
import java.util.FormattableFlags;
import java.util.Formatter;
import java.util.IllegalFormatConversionException;
import java.util.IllegalFormatFlagsException;
import java.util.IllegalFormatPrecisionException;
import java.util.Locale;
import java.util.MissingFormatArgumentException;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UnknownFormatConversionException;

import io.smallrye.common.constraint.Assert;

/**
 * A string formatter which can be customized.
 */
class Printf {

    private static final String someSpaces = "                                "; //32 spaces
    private static final String someZeroes = "00000000000000000000000000000000"; //32 zeros

    private final Locale locale;
    private volatile DateFormatSymbols dfs;

    public static final Printf DEFAULT = new Printf(Locale.getDefault(Locale.Category.FORMAT));

    public Printf(final Locale locale) {
        Assert.checkNotNullParam("locale", locale);
        this.locale = locale;
    }

    public Printf() {
        this(Locale.getDefault(Locale.Category.FORMAT));
    }

    public Locale getLocale() {
        return locale;
    }

    private static final int ST_INITIAL = 0;
    private static final int ST_PCT = 1;
    private static final int ST_TIME = 2;
    private static final int ST_WIDTH = 3;
    private static final int ST_DOT = 4;
    private static final int ST_PREC = 5;
    private static final int ST_DOLLAR = 6;

    public String format(String format, Object... params) {
        return formatDirect(new StringBuilder(), format, params).toString();
    }

    public <A extends Appendable> A formatBuffered(A destination, String format, Object... params) throws IOException {
        destination.append(formatDirect(new StringBuilder(format.length() << 1), format, params));
        return destination;
    }

    public StringBuilder formatDirect(StringBuilder destination, String format, Object... params) {
        int cp;
        int state = ST_INITIAL;
        GeneralFlags genFlags = GeneralFlags.NONE;
        NumericFlags numFlags = NumericFlags.NONE;
        int precision = -1;
        int width = -1;
        int argIdx = -1; // selected argument
        int lastArgIdx = -1;
        int crs = 0; // current argument cursor
        int start = -1; // for diagnostics
        Object argVal = null; // argument value
        for (int i = 0; i < format.length(); i = format.offsetByCodePoints(i, 1)) {
            cp = format.codePointAt(i);
            if (state == ST_INITIAL) {
                if (cp == '%') {
                    start = i;
                    state = ST_PCT;
                    genFlags = GeneralFlags.NONE;
                    numFlags = NumericFlags.NONE;
                    precision = -1;
                    width = -1;
                    lastArgIdx = argIdx;
                    argIdx = -1;
                    continue;
                } else {
                    destination.appendCodePoint(cp);
                    continue;
                }
            } else if (state == ST_PCT || state == ST_DOLLAR) {
                if (state == ST_PCT && cp == '<') {
                    if (lastArgIdx == -1)
                        throw new IllegalFormatFlagsException("<");
                    argIdx = lastArgIdx;
                    continue;
                }
                // select flags here
                switch (cp) {
                    case '.': {
                        state = ST_DOT;
                        continue;
                    }
                    case ' ': {
                        numFlags.forbid(NumericFlag.SPACE_POSITIVE);
                        numFlags = numFlags.with(NumericFlag.SPACE_POSITIVE);
                        if (numFlags.contains(NumericFlag.SIGN))
                            numFlags.forbid(NumericFlag.SPACE_POSITIVE);
                        continue;
                    }
                    case '+': {
                        numFlags.forbid(NumericFlag.SIGN);
                        numFlags = numFlags.with(NumericFlag.SIGN);
                        if (numFlags.contains(NumericFlag.SPACE_POSITIVE))
                            numFlags.forbid(NumericFlag.SIGN);
                        continue;
                    }
                    case '0': {
                        numFlags.forbid(NumericFlag.ZERO_PAD);
                        numFlags = numFlags.with(NumericFlag.ZERO_PAD);
                        if (genFlags.contains(GeneralFlag.LEFT_JUSTIFY))
                            numFlags.forbid(NumericFlag.ZERO_PAD);
                        continue;
                    }
                    case '-': {
                        genFlags.forbid(GeneralFlag.LEFT_JUSTIFY);
                        genFlags = genFlags.with(GeneralFlag.LEFT_JUSTIFY);
                        if (numFlags.contains(NumericFlag.ZERO_PAD))
                            genFlags.forbid(GeneralFlag.LEFT_JUSTIFY);
                        continue;
                    }
                    case '(': {
                        numFlags.forbid(NumericFlag.NEGATIVE_PARENTHESES);
                        numFlags = numFlags.with(NumericFlag.NEGATIVE_PARENTHESES);
                        continue;
                    }
                    case ',': {
                        numFlags.forbid(NumericFlag.LOCALE_GROUPING_SEPARATORS);
                        numFlags = numFlags.with(NumericFlag.LOCALE_GROUPING_SEPARATORS);
                        continue;
                    }
                    case '#': {
                        genFlags.forbid(GeneralFlag.ALTERNATE);
                        genFlags = genFlags.with(GeneralFlag.ALTERNATE);
                        continue;
                    }
                }
                // otherwise, fall thru
            } else if (state == ST_TIME) {
                // time-specific format specifiers
                numFlags.forbidAll();
                genFlags.forbid(GeneralFlag.ALTERNATE);
                if (precision != -1)
                    throw precisionException(precision);
                if (argVal == null) {
                    formatPlainString(destination, null, genFlags, width, -1);
                    continue;
                }
                TemporalAccessor ta;
                if (argVal instanceof Long) {
                    ta = ZonedDateTime.ofInstant(Instant.ofEpochMilli(((Long) argVal).longValue()), ZoneId.systemDefault());
                } else if (argVal instanceof Date) {
                    ta = ZonedDateTime.ofInstant(Instant.ofEpochMilli(((Date) argVal).getTime()), ZoneId.systemDefault());
                } else if (argVal instanceof Calendar) {
                    final Calendar calendar = (Calendar) argVal;
                    final TimeZone timeZone = calendar.getTimeZone();
                    ZoneId zoneId = timeZone == null ? ZoneId.systemDefault() : timeZone.toZoneId();
                    ta = ZonedDateTime.ofInstant(calendar.toInstant(), zoneId);
                } else if (argVal instanceof TemporalAccessor) {
                    ta = (TemporalAccessor) argVal;
                } else {
                    throw new IllegalFormatConversionException((char) cp, argVal.getClass());
                }
                switch (cp) {
                    // locale-based names
                    case 'A': {
                        formatTimeTextField(destination, ta, ChronoField.DAY_OF_WEEK, getDateFormatSymbols().getWeekdays(),
                                genFlags, width);
                        break;
                    }
                    case 'a': {
                        formatTimeTextField(destination, ta, ChronoField.DAY_OF_WEEK, getDateFormatSymbols().getShortWeekdays(),
                                genFlags, width);
                        break;
                    }
                    case 'B': {
                        formatTimeTextField(destination, ta, ChronoField.MONTH_OF_YEAR, getDateFormatSymbols().getMonths(),
                                genFlags, width);
                        break;
                    }
                    case 'h': // synonym for 'b'
                    case 'b': {
                        formatTimeTextField(destination, ta, ChronoField.MONTH_OF_YEAR, getDateFormatSymbols().getShortMonths(),
                                genFlags, width);
                        break;
                    }
                    case 'p': {
                        formatTimeTextField(destination, ta, ChronoField.AMPM_OF_DAY, getDateFormatSymbols().getAmPmStrings(),
                                genFlags, width);
                        break;
                    }

                    // chrono fields
                    case 'C': {
                        formatTimeField(destination, ta, CENTURY_OF_YEAR, genFlags, width, 2);
                        break;
                    }
                    case 'd': {
                        formatTimeField(destination, ta, ChronoField.DAY_OF_MONTH, genFlags, width, 2);
                        break;
                    }
                    case 'e': {
                        formatTimeField(destination, ta, ChronoField.DAY_OF_MONTH, genFlags, width, 1);
                        break;
                    }
                    case 'H': {
                        formatTimeField(destination, ta, ChronoField.HOUR_OF_DAY, genFlags, width, 2);
                        break;
                    }
                    case 'I': {
                        formatTimeField(destination, ta, ChronoField.CLOCK_HOUR_OF_AMPM, genFlags, width, 2);
                        break;
                    }
                    case 'j': {
                        formatTimeField(destination, ta, ChronoField.DAY_OF_YEAR, genFlags, width, 3);
                        break;
                    }
                    case 'k': {
                        formatTimeField(destination, ta, ChronoField.HOUR_OF_DAY, genFlags, width, 1);
                        break;
                    }
                    case 'L': {
                        formatTimeField(destination, ta, ChronoField.MILLI_OF_SECOND, genFlags, width, 3);
                        break;
                    }
                    case 'l': {
                        formatTimeField(destination, ta, ChronoField.CLOCK_HOUR_OF_AMPM, genFlags, width, 1);
                        break;
                    }
                    case 'M': {
                        formatTimeField(destination, ta, ChronoField.MINUTE_OF_HOUR, genFlags, width, 2);
                        break;
                    }
                    case 'm': {
                        formatTimeField(destination, ta, ChronoField.MONTH_OF_YEAR, genFlags, width, 2);
                        break;
                    }
                    case 'N': {
                        formatTimeField(destination, ta, ChronoField.NANO_OF_SECOND, genFlags, width, 9);
                        break;
                    }
                    case 'Q': {
                        formatTimeField(destination, ta, MILLIS_OF_INSTANT, genFlags, width, 1);
                        break;
                    }
                    case 'S': {
                        formatTimeField(destination, ta, ChronoField.SECOND_OF_MINUTE, genFlags, width, 2);
                        break;
                    }
                    case 's': {
                        formatTimeField(destination, ta, ChronoField.INSTANT_SECONDS, genFlags, width, 2);
                        break;
                    }
                    case 'Y': {
                        formatTimeField(destination, ta, ChronoField.YEAR_OF_ERA, genFlags, width, 4);
                        break;
                    }
                    case 'y': {
                        formatTimeField(destination, ta, YEAR_OF_CENTURY, genFlags, width, 2);
                        break;
                    }

                    // zone strings
                    case 'Z': {
                        formatTimeZoneId(destination, ta, genFlags, width);
                        break;
                    }
                    case 'z': {
                        formatTimeZoneOffset(destination, ta, genFlags, width);
                        break;
                    }

                    // compositions
                    case 'c': {
                        final StringBuilder b = new StringBuilder();
                        formatTimeTextField(b, ta, ChronoField.DAY_OF_WEEK, getDateFormatSymbols().getShortWeekdays(), genFlags,
                                -1);
                        b.append(' ');
                        formatTimeTextField(b, ta, ChronoField.MONTH_OF_YEAR, getDateFormatSymbols().getShortMonths(), genFlags,
                                -1);
                        b.append(' ');
                        formatTimeField(b, ta, ChronoField.DAY_OF_MONTH, genFlags, -1, 2);
                        b.append(' ');
                        formatTimeField(b, ta, ChronoField.HOUR_OF_DAY, genFlags, -1, 2);
                        b.append(':');
                        formatTimeField(b, ta, ChronoField.MINUTE_OF_HOUR, genFlags, -1, 2);
                        b.append(':');
                        formatTimeField(b, ta, ChronoField.SECOND_OF_MINUTE, genFlags, -1, 2);
                        b.append(' ');
                        formatTimeZoneId(b, ta, genFlags.with(GeneralFlag.UPPERCASE), width);
                        b.append(' ');
                        formatTimeField(b, ta, ChronoField.YEAR_OF_ERA, genFlags, -1, 4);
                        appendStr(destination, genFlags, width, -1, b.toString());
                        break;
                    }
                    case 'D': {
                        final StringBuilder b = new StringBuilder();
                        formatTimeField(b, ta, ChronoField.MONTH_OF_YEAR, genFlags, -1, 2);
                        b.append('/');
                        formatTimeField(b, ta, ChronoField.DAY_OF_MONTH, genFlags, -1, 2);
                        b.append('/');
                        formatTimeField(b, ta, YEAR_OF_CENTURY, genFlags, -1, 2);
                        appendStr(destination, genFlags, width, -1, b.toString());
                        break;
                    }
                    case 'F': {
                        final StringBuilder b = new StringBuilder();
                        formatTimeField(b, ta, ChronoField.YEAR_OF_ERA, genFlags, -1, 4);
                        b.append('-');
                        formatTimeField(b, ta, ChronoField.MONTH_OF_YEAR, genFlags, -1, 2);
                        b.append('-');
                        formatTimeField(b, ta, ChronoField.DAY_OF_MONTH, genFlags, -1, 2);
                        appendStr(destination, genFlags, width, -1, b.toString());
                        break;
                    }
                    case 'R': {
                        final StringBuilder b = new StringBuilder();
                        formatTimeField(b, ta, ChronoField.HOUR_OF_DAY, genFlags, -1, 2);
                        b.append(':');
                        formatTimeField(b, ta, ChronoField.MINUTE_OF_HOUR, genFlags, -1, 2);
                        b.append(':');
                        formatTimeField(b, ta, ChronoField.SECOND_OF_MINUTE, genFlags, -1, 2);
                        appendStr(destination, genFlags, width, -1, b.toString());
                        break;
                    }
                    case 'r': {
                        final StringBuilder b = new StringBuilder();
                        formatTimeField(b, ta, ChronoField.HOUR_OF_DAY, genFlags, -1, 2);
                        b.append(':');
                        formatTimeField(b, ta, ChronoField.MINUTE_OF_HOUR, genFlags, -1, 2);
                        b.append(':');
                        formatTimeField(b, ta, ChronoField.SECOND_OF_MINUTE, genFlags, -1, 2);
                        b.append(' ');
                        formatTimeTextField(b, ta, ChronoField.AMPM_OF_DAY, getDateFormatSymbols().getAmPmStrings(),
                                genFlags.with(GeneralFlag.UPPERCASE), width);
                        appendStr(destination, genFlags, width, -1, b.toString());
                        break;
                    }
                    case 'T': {
                        final StringBuilder b = new StringBuilder();
                        formatTimeField(b, ta, ChronoField.HOUR_OF_DAY, genFlags, -1, 2);
                        b.append(':');
                        formatTimeField(b, ta, ChronoField.MINUTE_OF_HOUR, genFlags, -1, 2);
                        b.append(':');
                        formatTimeField(b, ta, ChronoField.SECOND_OF_MINUTE, genFlags, -1, 2);
                        appendStr(destination, genFlags, width, -1, b.toString());
                        break;
                    }
                    default: {
                        throw unknownFormat(format, i);
                    }
                }
                state = ST_INITIAL;
                continue;
            } else if (state == ST_WIDTH) {
                switch (cp) {
                    case '$': {
                        if (genFlags != GeneralFlags.NONE || numFlags != NumericFlags.NONE) {
                            throw unknownFormat(format, i);
                        }
                        argIdx = width;
                        width = -1;
                        state = ST_DOLLAR;
                        continue;
                    }
                    case '.': {
                        state = ST_DOT;
                        continue;
                    }
                }
                // otherwise, fall thru
            }
            if ('0' <= cp && cp <= '9') {
                switch (state) {
                    case ST_DOLLAR:
                    case ST_PCT: {
                        state = ST_WIDTH;
                        width = cp - '0';
                        continue;
                    }
                    case ST_WIDTH: {
                        width = Math.addExact(Math.multiplyExact(width, 10), cp - '0');
                        continue;
                    }
                    case ST_DOT: {
                        state = ST_PREC;
                        precision = cp - '0';
                        continue;
                    }
                    case ST_PREC: {
                        precision = Math.addExact(Math.multiplyExact(precision, 10), cp - '0');
                        continue;
                    }
                    default: {
                        throw Assert.impossibleSwitchCase(state);
                    }
                }
            }
            if (state == ST_DOT) {
                throw unknownFormat(format, i);
            }
            // basic format specifiers
            if (cp != 'n' && cp != '%') {
                // capture argument
                if (argIdx != -1) {
                    if (argIdx - 1 >= params.length) {
                        throw new MissingFormatArgumentException(
                                format.substring(start, cp == 't' || cp == 'T' ? i + 2 : i + 1));
                    }
                    argVal = params[argIdx - 1];
                } else {
                    if (crs >= params.length) {
                        throw new MissingFormatArgumentException(
                                format.substring(start, cp == 't' || cp == 'T' ? i + 2 : i + 1));
                    }
                    argVal = params[crs++];
                    argIdx = crs; // crs is 0-based, argIdx & lastArgIdx are 1-based
                }
            }
            switch (cp) {
                case '%': {
                    genFlags.forbidAllBut(GeneralFlag.LEFT_JUSTIFY); // but it's ignored anyway
                    numFlags.forbidAll();
                    if (precision != -1 || state == ST_PREC)
                        throw precisionException(precision);
                    formatPercent(destination);
                    break;
                }
                case 'A':
                case 'a': {
                    // TODO support hex FP
                    throw unknownFormat(format, i);
                }
                case 'B':
                case 'b': {
                    numFlags.forbidAll();
                    genFlags.forbid(GeneralFlag.ALTERNATE);
                    if (Character.isUpperCase(cp))
                        genFlags = genFlags.with(GeneralFlag.UPPERCASE);
                    if (argVal != null && !(argVal instanceof Boolean))
                        throw new IllegalFormatConversionException((char) cp, argVal.getClass());
                    formatBoolean(destination, checkType(cp, argVal, Boolean.class), genFlags, width, precision);
                    break;
                }
                case 'C':
                case 'c': {
                    numFlags.forbidAll();
                    genFlags.forbidAllBut(GeneralFlag.LEFT_JUSTIFY);
                    if (Character.isUpperCase(cp))
                        genFlags = genFlags.with(GeneralFlag.UPPERCASE);
                    if (precision != -1 || state == ST_PREC)
                        throw precisionException(precision);
                    int cpa;
                    if (argVal == null) {
                        appendStr(destination, genFlags, width, precision, "null");
                        break;
                    } else if (argVal instanceof Character) {
                        cpa = ((Character) argVal).charValue();
                    } else if (argVal instanceof Integer) {
                        cpa = ((Integer) argVal).intValue();
                    } else {
                        throw new IllegalFormatConversionException((char) cp, argVal.getClass());
                    }
                    formatCharacter(destination, cpa, genFlags, width, precision);
                    break;
                }
                case 'd': {
                    genFlags.forbid(GeneralFlag.ALTERNATE);
                    if (precision != -1 || state == ST_PREC)
                        throw precisionException(precision);
                    formatDecimalInteger(destination, checkType(cp, argVal, Number.class, Byte.class, Short.class,
                            Integer.class, Long.class, BigInteger.class), genFlags, numFlags, width);
                    break;
                }
                case 'E':
                case 'e':
                case 'f':
                case 'G':
                case 'g': {
                    if (Character.isUpperCase(cp))
                        genFlags = genFlags.with(GeneralFlag.UPPERCASE);
                    if (argVal != null && !(argVal instanceof Float) && !(argVal instanceof Double)
                            && !(argVal instanceof BigDecimal)) {
                        throw new IllegalFormatConversionException((char) cp, argVal.getClass());
                    }
                    Number item = checkType(cp, argVal, Number.class, Float.class, Double.class, BigDecimal.class);
                    if (cp == 'e' || cp == 'E') {
                        formatFloatingPointSci(destination, item, genFlags, numFlags, width, precision);
                        break;
                    } else if (cp == 'f') {
                        formatFloatingPointDecimal(destination, item, genFlags, numFlags, width, precision);
                        break;
                    } else {
                        assert cp == 'g' || cp == 'G';
                        formatFloatingPointGeneral(destination, item, genFlags, numFlags, width, precision);
                        break;
                    }
                }
                case 'H':
                case 'h': {
                    genFlags.forbid(GeneralFlag.ALTERNATE);
                    numFlags.forbidAll();
                    formatHashCode(destination, argVal, genFlags, width, precision);
                    break;
                }
                case 'n': {
                    numFlags.forbidAll();
                    genFlags.forbidAll();
                    formatLineSeparator(destination);
                    break;
                }
                case 'o': {
                    numFlags.forbidAllBut(NumericFlag.ZERO_PAD);
                    if (precision != -1 || state == ST_PREC)
                        throw precisionException(precision);
                    formatOctalInteger(destination, checkType(cp, argVal, Number.class, Byte.class, Short.class, Integer.class,
                            Long.class, BigInteger.class), genFlags, numFlags, width);
                    break;
                }
                case 's':
                case 'S': {
                    numFlags.forbidAll();
                    if (Character.isUpperCase(cp))
                        genFlags = genFlags.with(GeneralFlag.UPPERCASE);
                    if (argVal instanceof Formattable) {
                        formatFormattableString(destination, (Formattable) argVal, genFlags, width, precision);
                    } else {
                        formatPlainString(destination, argVal, genFlags, width, precision);
                    }
                    break;
                }
                case 'T':
                case 't': {
                    if (Character.isUpperCase(cp))
                        genFlags = genFlags.with(GeneralFlag.UPPERCASE);
                    state = ST_TIME;
                    continue;
                }
                case 'X':
                case 'x': {
                    numFlags.forbidAllBut(NumericFlag.ZERO_PAD);
                    if (Character.isUpperCase(cp))
                        genFlags = genFlags.with(GeneralFlag.UPPERCASE);
                    if (precision != -1 || state == ST_PREC)
                        throw precisionException(precision);
                    formatHexInteger(destination, checkType(cp, argVal, Number.class, Byte.class, Short.class, Integer.class,
                            Long.class, BigInteger.class), genFlags, numFlags, width);
                    break;
                }
                default: {
                    throw unknownFormat(format, i);
                }
            }
            state = ST_INITIAL;
            //continue;
        }
        return destination;
    }

    protected static void appendSpaces(StringBuilder target, int cnt) {
        appendFiller(target, someSpaces, cnt);
    }

    protected static void appendZeros(StringBuilder target, int cnt) {
        appendFiller(target, someZeroes, cnt);
    }

    protected DateFormatSymbols getDateFormatSymbols() {
        DateFormatSymbols dfs = this.dfs;
        if (dfs == null) {
            synchronized (this) {
                dfs = this.dfs;
                if (dfs == null) {
                    this.dfs = dfs = DateFormatSymbols.getInstance(locale);
                }
            }
        }
        return dfs;
    }

    protected void formatTimeTextField(final StringBuilder target, final TemporalAccessor ta, final TemporalField field,
            final String[] symbols, final GeneralFlags genFlags, final int width) {
        final int baseIdx = ta.get(field);
        // fix offset fields
        final int idx = field == ChronoField.MONTH_OF_YEAR ? baseIdx - 1
                : field == ChronoField.DAY_OF_WEEK ? (baseIdx + 1) % 7 : baseIdx;
        appendStr(target, genFlags, width, -1, symbols[idx]);
    }

    protected void formatTimeZoneId(final StringBuilder target, final TemporalAccessor ta, final GeneralFlags genFlags,
            final int width) {
        final boolean upper = genFlags.contains(GeneralFlag.UPPERCASE);
        final ZoneId zoneId = ta.query(TemporalQueries.zone());
        if (zoneId == null) {
            throw new IllegalFormatConversionException(upper ? 'T' : 't', ta.getClass());
        }
        String output;
        if (ta.isSupported(ChronoField.INSTANT_SECONDS)) {
            final boolean dst = zoneId.getRules().isDaylightSavings(Instant.from(ta));
            output = TimeZone.getTimeZone(zoneId).getDisplayName(dst, 0, locale);
        } else {
            output = zoneId.getId();
        }
        appendStr(target, genFlags, width, -1, output);
    }

    protected void formatTimeZoneOffset(final StringBuilder target, final TemporalAccessor ta, final GeneralFlags genFlags,
            final int width) {
        final int offset = ta.get(ChronoField.OFFSET_SECONDS);
        final int absOffset = Math.abs(offset);
        final int minutes = (absOffset / 60) % 60;
        final int hours = (absOffset / 3600);
        final boolean lj = genFlags.contains(GeneralFlag.LEFT_JUSTIFY);
        if (width > 5 && !lj) {
            appendSpaces(target, width - 5);
        }
        target.append(offset > 0 ? '+' : '-');
        if (hours < 10)
            target.append('0');
        target.append(hours);
        if (minutes < 10)
            target.append('0');
        target.append(minutes);
        if (width > 5 && lj) {
            appendSpaces(target, width - 5);
        }
    }

    protected void formatTimeField(final StringBuilder target, final TemporalAccessor ta, final TemporalField field,
            final GeneralFlags genFlags, final int width, final int zeroPad) {
        final long val = ta.getLong(field);
        final String valStr = Long.toString(val);
        final int length = valStr.length();
        final int extLen = max(zeroPad, length);
        final boolean lj = genFlags.contains(GeneralFlag.LEFT_JUSTIFY);
        if (width > extLen && !lj) {
            appendSpaces(target, width - extLen);
        }
        if (zeroPad > length) {
            appendZeros(target, zeroPad - length);
        }
        target.append(valStr);
        if (width > extLen && lj) {
            appendSpaces(target, width - extLen);
        }
    }

    protected void formatPercent(StringBuilder target) {
        appendChar(target, GeneralFlags.NONE, 1, -1, '%');
    }

    protected void formatLineSeparator(StringBuilder target) {
        target.append(System.lineSeparator());
    }

    protected void formatFormattableString(StringBuilder target, Formattable formattable, GeneralFlags genFlags, int width,
            int precision) {
        int fmtFlags = 0;
        if (genFlags.contains(GeneralFlag.LEFT_JUSTIFY))
            fmtFlags |= FormattableFlags.LEFT_JUSTIFY;
        if (genFlags.contains(GeneralFlag.UPPERCASE))
            fmtFlags |= FormattableFlags.UPPERCASE;
        if (genFlags.contains(GeneralFlag.ALTERNATE))
            fmtFlags |= FormattableFlags.ALTERNATE;
        // make a dummy Formatter to appease the constraints of the API
        formattable.formatTo(new Formatter(target), fmtFlags, width, precision);
    }

    protected void formatPlainString(StringBuilder target, Object item, GeneralFlags genFlags, int width, int precision) {
        appendStr(target, genFlags, width, precision, String.valueOf(item));
    }

    protected void formatBoolean(StringBuilder target, Object item, GeneralFlags genFlags, int width, int precision) {
        appendStr(target, genFlags, width, precision,
                item instanceof Boolean ? item.toString() : Boolean.toString(item != null));
    }

    protected void formatHashCode(StringBuilder target, Object item, GeneralFlags genFlags, int width, int precision) {
        appendStr(target, genFlags, width, precision, Integer.toHexString(Objects.hashCode(item)));
    }

    protected void formatCharacter(StringBuilder target, int codePoint, GeneralFlags genFlags, int width, int precision) {
        if (Character.isBmpCodePoint(codePoint)) {
            appendChar(target, genFlags, width, precision, (char) codePoint);
        } else {
            appendStr(target, genFlags, width, precision, new String(new int[] { codePoint }, 0, 1));
        }
    }

    protected void formatDecimalInteger(StringBuilder target, Number item, GeneralFlags genFlags, NumericFlags numFlags,
            int width) {
        if (item == null) {
            appendStr(target, genFlags, width, -1, "null");
        } else {
            DecimalFormat fmt = (DecimalFormat) NumberFormat.getIntegerInstance(locale);
            if (numFlags.contains(NumericFlag.SIGN)) {
                fmt.setPositivePrefix("+");
            } else if (numFlags.contains(NumericFlag.SPACE_POSITIVE)) {
                fmt.setPositivePrefix(" ");
            } else {
                fmt.setPositivePrefix("");
            }
            fmt.setPositiveSuffix("");
            if (numFlags.contains(NumericFlag.NEGATIVE_PARENTHESES)) {
                fmt.setNegativePrefix("(");
                fmt.setNegativeSuffix(")");
            } else {
                fmt.setNegativePrefix("-");
                fmt.setNegativeSuffix("");
            }
            fmt.setGroupingUsed(numFlags.contains(NumericFlag.LOCALE_GROUPING_SEPARATORS));
            if (numFlags.contains(NumericFlag.ZERO_PAD)) {
                fmt.setMinimumIntegerDigits(width);
            }
            fmt.setDecimalSeparatorAlwaysShown(genFlags.contains(GeneralFlag.ALTERNATE));
            appendStr(target, genFlags, width, -1, fmt.format(item));
        }
    }

    protected void formatOctalInteger(StringBuilder target, Number item, GeneralFlags genFlags, NumericFlags numFlags,
            int width) {
        if (item == null) {
            appendStr(target, genFlags, width, -1, "null");
        } else {
            final boolean addRadix = genFlags.contains(GeneralFlag.ALTERNATE);
            final int fillCount = max(0, width - (bitLengthOf(item) + 2) / 3 - (addRadix ? 1 : 0));
            final boolean lj = genFlags.contains(GeneralFlag.LEFT_JUSTIFY);
            if (numFlags.contains(NumericFlag.ZERO_PAD)) {
                // write zeros first
                if (addRadix)
                    target.append('0');
                appendZeros(target, fillCount);
            } else if (lj) {
                if (addRadix)
                    target.append('0');
            } else {
                // ! LEFT_JUSTIFY
                // write spaces first
                appendSpaces(target, fillCount);
                if (addRadix)
                    target.append('0');
            }
            appendOctal(target, item);
            if (lj) {
                appendSpaces(target, fillCount);
            }
        }
    }

    protected void formatHexInteger(StringBuilder target, Number item, GeneralFlags genFlags, NumericFlags numFlags,
            int width) {
        if (item == null) {
            appendStr(target, genFlags, width, -1, "null");
        } else {
            final boolean upper = genFlags.contains(GeneralFlag.UPPERCASE);
            final boolean addRadix = genFlags.contains(GeneralFlag.ALTERNATE);
            final int fillCount = max(0, width - (bitLengthOf(item) + 3) / 4 - (addRadix ? 2 : 0));
            final boolean lj = genFlags.contains(GeneralFlag.LEFT_JUSTIFY);
            if (numFlags.contains(NumericFlag.ZERO_PAD)) {
                // write zeros first
                if (addRadix)
                    target.append(upper ? "0X" : "0x");
                appendZeros(target, fillCount);
            } else if (lj) {
                if (addRadix)
                    target.append(upper ? "0X" : "0x");
            } else {
                // ! LEFT_JUSTIFY
                // write spaces first
                appendSpaces(target, fillCount);
                if (addRadix)
                    target.append(upper ? "0X" : "0x");
            }
            appendHex(target, item, upper);
            if (lj) {
                appendSpaces(target, fillCount);
            }
        }
    }

    protected void formatFloatingPointSci(StringBuilder target, Number item, GeneralFlags genFlags, NumericFlags numFlags,
            int width, int precision) {
        if (item == null) {
            appendStr(target, genFlags, width, precision, "null");
        } else {
            final boolean upper = genFlags.contains(GeneralFlag.UPPERCASE);
            final DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(locale);
            if (negativeExp(item)) {
                sym.setExponentSeparator(upper ? "E" : "e");
            } else {
                sym.setExponentSeparator(upper ? "E+" : "e+");
            }
            formatDFP(target, item, genFlags, numFlags, width, precision == -1 ? 6 : precision == 0 ? 1 : precision, true, sym,
                    "0.#E00");
        }
    }

    protected void formatFloatingPointDecimal(StringBuilder target, Number item, GeneralFlags genFlags, NumericFlags numFlags,
            int width, int precision) {
        if (item == null) {
            appendStr(target, genFlags, width, precision, "null");
        } else {
            formatDFP(target, item, genFlags, numFlags, width, precision == 0 ? 1 : precision, false,
                    DecimalFormatSymbols.getInstance(locale), "0.#");
        }
    }

    protected void formatFloatingPointGeneral(StringBuilder target, Number item, GeneralFlags genFlags, NumericFlags numFlags,
            int width, int precision) {
        if (item == null) {
            appendStr(target, genFlags, width, precision, "null");
        } else {
            boolean sci;
            if (item instanceof BigDecimal) {
                final BigDecimal mag = ((BigDecimal) item).abs();
                sci = mag.compareTo(NEG_TEN_EM4) < 0 || mag.compareTo(BigDecimal.valueOf(10, precision)) >= 0;
            } else if (item instanceof Float) {
                final float fv = Math.abs(item.floatValue());
                sci = Float.isFinite(fv) && (fv < 10e-4f || fv >= Math.pow(10, precision));
            } else {
                assert item instanceof Double;
                final double dv = Math.abs(item.doubleValue());
                sci = Double.isFinite(dv) && (dv < 10e-4f || dv >= Math.pow(10, precision));
            }
            if (sci) {
                formatFloatingPointSci(target, item, genFlags, numFlags, width, precision);
            } else {
                formatFloatingPointDecimal(target, item, genFlags, numFlags, width, precision);
            }
        }
    }

    private void formatDFP(final StringBuilder target, final Number item, final GeneralFlags genFlags,
            final NumericFlags numFlags, final int width, final int precision, final boolean oneIntDigit,
            final DecimalFormatSymbols sym, final String s) {
        if (!(item instanceof BigDecimal)) {
            final double dv = item.doubleValue();
            if (!Double.isFinite(dv)) {
                appendStr(target, genFlags, width, -1, Double.toString(dv));
                return;
            }
        }
        DecimalFormat fmt = new DecimalFormat(s, sym);
        if (numFlags.contains(NumericFlag.SIGN)) {
            fmt.setPositivePrefix("+");
        } else if (numFlags.contains(NumericFlag.SPACE_POSITIVE)) {
            fmt.setPositivePrefix(" ");
        } else {
            fmt.setPositivePrefix("");
        }
        fmt.setPositiveSuffix("");
        if (numFlags.contains(NumericFlag.NEGATIVE_PARENTHESES)) {
            fmt.setNegativePrefix("(");
            fmt.setNegativeSuffix(")");
        } else {
            fmt.setNegativePrefix("-");
            fmt.setNegativeSuffix("");
        }
        fmt.setGroupingUsed(numFlags.contains(NumericFlag.LOCALE_GROUPING_SEPARATORS));
        fmt.setMinimumFractionDigits(precision == -1 ? 1 : precision);
        fmt.setMaximumFractionDigits(precision == -1 ? Integer.MAX_VALUE : precision);
        fmt.setMinimumIntegerDigits(1);
        if (oneIntDigit) {
            fmt.setMaximumIntegerDigits(1);
        }
        fmt.setRoundingMode(RoundingMode.HALF_UP);
        final AttributedCharacterIterator iterator = fmt.formatToCharacterIterator(item);
        final int end = iterator.getEndIndex();
        final boolean lj = genFlags.contains(GeneralFlag.LEFT_JUSTIFY);
        final boolean zp = numFlags.contains(NumericFlag.ZERO_PAD);
        if (!lj && !zp && width > end) {
            appendSpaces(target, width - end);
        }
        while (iterator.getAttribute(NumberFormat.Field.SIGN) != null) {
            target.append(iterator.current());
            iterator.next(); // move
        }
        assert iterator.getAttribute(NumberFormat.Field.INTEGER) != null;
        if (zp && width > end) {
            appendZeros(target, width - end);
        }
        // now continue to the end
        while (iterator.getIndex() < end) {
            target.append(iterator.current());
            iterator.next(); // move
        }
        if (lj && width > end) {
            appendSpaces(target, width - end);
        }
    }

    private static final BigDecimal NEG_ONE = BigDecimal.ONE.negate();
    private static final BigDecimal NEG_TEN_EM4 = BigDecimal.valueOf(10, -4);

    private boolean negativeExp(final Number item) {
        if (item instanceof BigDecimal) {
            final BigDecimal bigDecimal = (BigDecimal) item;
            return bigDecimal.compareTo(BigDecimal.ONE) < 0 && bigDecimal.compareTo(NEG_ONE) > 0;
        } else {
            final double val = item.doubleValue();
            return -1 < val && val < 1;
        }
    }

    private static int bitLengthOf(final Number item) {
        if (item instanceof Byte) {
            return 32 - Integer.numberOfLeadingZeros(item.byteValue() & 0xff);
        } else if (item instanceof Short) {
            return 32 - Integer.numberOfLeadingZeros(item.shortValue() & 0xffff);
        } else if (item instanceof Integer) {
            return 32 - Integer.numberOfLeadingZeros(item.intValue());
        } else if (item instanceof Long) {
            return 64 - Long.numberOfLeadingZeros(item.longValue());
        } else {
            assert item instanceof BigInteger;
            return ((BigInteger) item).bitLength();
        }
    }

    private static void appendOctal(StringBuilder target, final Number item) {
        if (item instanceof Byte) {
            target.append(Integer.toOctalString(item.byteValue() & 0xff));
        } else if (item instanceof Short) {
            target.append(Integer.toOctalString(item.shortValue() & 0xffff));
        } else if (item instanceof Integer) {
            target.append(Integer.toOctalString(item.shortValue()));
        } else if (item instanceof Long) {
            target.append(Long.toOctalString(item.longValue()));
        } else if (item instanceof BigInteger) {
            final BigInteger bi = (BigInteger) item;
            final int bl = bi.bitLength();
            if (bl <= 64) {
                target.append(Long.toOctalString(bi.longValue()));
            } else {
                int max = ((bl + 2) / 3) * 3;
                for (int i = 0; i < max; i += 3) {
                    int val = 0;
                    if (bi.testBit(max - i))
                        val |= 0b100;
                    if (bi.testBit(max - i - 1))
                        val |= 0b010;
                    if (bi.testBit(max - i - 2))
                        val |= 0b001;
                    target.append(val);
                }
            }
        }
    }

    private static void appendHex(final StringBuilder target, final Number item, final boolean upper) {
        if (item instanceof Byte) {
            final String str = Integer.toHexString(item.byteValue() & 0xff);
            target.append(upper ? str.toUpperCase() : str);
        } else if (item instanceof Short) {
            final String str = Integer.toHexString(item.shortValue() & 0xffff);
            target.append(upper ? str.toUpperCase() : str);
        } else if (item instanceof Integer) {
            final String str = Integer.toHexString(item.shortValue());
            target.append(upper ? str.toUpperCase() : str);
        } else if (item instanceof Long) {
            final String str = Long.toHexString(item.longValue());
            target.append(upper ? str.toUpperCase() : str);
        } else if (item instanceof BigInteger) {
            final BigInteger bi = (BigInteger) item;
            final int bl = bi.bitLength();
            if (bl <= 64) {
                target.append(Long.toHexString(bi.longValue()));
            } else {
                int max = ((bl + 3) / 4) * 4;
                for (int i = 0; i < max; i += 4) {
                    int val = 0;
                    if (bi.testBit(max - i))
                        val |= 0b1000;
                    if (bi.testBit(max - i - 1))
                        val |= 0b0100;
                    if (bi.testBit(max - i - 2))
                        val |= 0b0010;
                    if (bi.testBit(max - i - 3))
                        val |= 0b0001;
                    if (val > 9)
                        val += upper ? 'A' : 'a' - 10;
                    target.append((char) (val > 9 ? val - 10 + (upper ? 'A' : 'a') : val + '0'));
                }
            }
        }
    }

    private void appendChar(final StringBuilder target, GeneralFlags genFlags, final int width, final int precision,
            final char c) {
        if (genFlags.contains(GeneralFlag.UPPERCASE) && Character.isLowerCase(c)) {
            appendStr(target, genFlags, width, precision, Character.toString(c));
        } else if (width <= 1) {
            target.append(c);
        } else if (genFlags.contains(GeneralFlag.LEFT_JUSTIFY)) {
            target.append(c);
            appendSpaces(target, width - 1);
        } else {
            appendSpaces(target, width - 1);
            target.append(c);
        }
    }

    private void appendStr(final StringBuilder target, GeneralFlags genFlags, final int width, final int precision,
            final String itemStr) {
        String str = genFlags.contains(GeneralFlag.UPPERCASE) ? itemStr.toUpperCase(locale) : itemStr;
        if (width == -1 && precision == -1) {
            target.append(str);
        } else {
            final int length = str.codePointCount(0, str.length());
            if (precision != -1 && precision < length) {
                str = str.substring(0, precision);
            }
            if (width != -1 && length < width) {
                // fill
                if (genFlags.contains(GeneralFlag.LEFT_JUSTIFY)) {
                    target.append(str);
                    appendSpaces(target, width - length);
                } else {
                    appendSpaces(target, width - length);
                    target.append(str);
                }
            } else {
                target.append(str);
            }
        }
    }

    @SafeVarargs
    private static <T> T checkType(int convCp, Object arg, Class<T> commonType, Class<? extends T>... allowedSubTypes) {
        if (arg == null)
            return null;
        if (commonType.isInstance(arg)) {
            if (allowedSubTypes.length == 0)
                return commonType.cast(arg);
            for (Class<? extends T> subType : allowedSubTypes) {
                if (subType.isInstance(arg))
                    return commonType.cast(arg);
            }
        }
        throw new IllegalFormatConversionException((char) convCp, arg.getClass());
    }

    private static void appendFiller(final StringBuilder target, final String filler, int cnt) {
        while (cnt > 32) {
            target.append(filler);
            cnt -= 32;
        }
        target.append(filler, 0, cnt);
    }

    private static IllegalFormatPrecisionException precisionException(int prec) {
        return new IllegalFormatPrecisionException(prec);
    }

    private static UnknownFormatConversionException unknownFormat(final String format, final int i) {
        return unknownFormat(format.substring(i, format.offsetByCodePoints(i, 1)));
    }

    private static UnknownFormatConversionException unknownFormat(final String arg) {
        return new UnknownFormatConversionException(arg);
    }

    private static final TemporalField MILLIS_OF_INSTANT = new TemporalField() {
        public TemporalUnit getBaseUnit() {
            return ChronoUnit.MILLIS;
        }

        public TemporalUnit getRangeUnit() {
            return ChronoUnit.FOREVER;
        }

        public ValueRange range() {
            return ValueRange.of(Long.MIN_VALUE, Long.MAX_VALUE);
        }

        public boolean isDateBased() {
            return true;
        }

        public boolean isTimeBased() {
            return false;
        }

        public boolean isSupportedBy(final TemporalAccessor temporal) {
            return temporal.isSupported(ChronoField.INSTANT_SECONDS) && temporal.isSupported(ChronoField.MILLI_OF_SECOND);
        }

        public ValueRange rangeRefinedBy(final TemporalAccessor temporal) {
            return range();
        }

        public long getFrom(final TemporalAccessor temporal) {
            return temporal.get(ChronoField.INSTANT_SECONDS) * 1000L + temporal.get(ChronoField.MILLI_OF_SECOND);
        }

        @SuppressWarnings("unchecked")
        public <R extends Temporal> R adjustInto(final R temporal, final long newValue) {
            final long millis = newValue % 1000L;
            final long seconds = newValue / 1000L;
            return (R) temporal.with(ChronoField.INSTANT_SECONDS, millis).with(ChronoField.MILLI_OF_SECOND, seconds);
        }
    };

    private static final TemporalField YEAR_OF_CENTURY = new TemporalField() {
        public TemporalUnit getBaseUnit() {
            return ChronoUnit.YEARS;
        }

        public TemporalUnit getRangeUnit() {
            return ChronoUnit.CENTURIES;
        }

        public ValueRange range() {
            return ValueRange.of(0, 99);
        }

        public boolean isDateBased() {
            return false;
        }

        public boolean isTimeBased() {
            return false;
        }

        public boolean isSupportedBy(final TemporalAccessor temporal) {
            return temporal.isSupported(ChronoField.YEAR);
        }

        public ValueRange rangeRefinedBy(final TemporalAccessor temporal) {
            return range();
        }

        public long getFrom(final TemporalAccessor temporal) {
            return temporal.get(ChronoField.YEAR) % 100;
        }

        @SuppressWarnings("unchecked")
        public <R extends Temporal> R adjustInto(final R temporal, final long newValue) {
            return (R) temporal.with(ChronoField.YEAR, (temporal.get(ChronoField.YEAR) / 100) * 100 + newValue);
        }
    };

    private static final TemporalField CENTURY_OF_YEAR = new TemporalField() {
        public TemporalUnit getBaseUnit() {
            return ChronoUnit.YEARS;
        }

        public TemporalUnit getRangeUnit() {
            return ChronoUnit.CENTURIES;
        }

        public ValueRange range() {
            return ValueRange.of(Long.MIN_VALUE, Long.MAX_VALUE);
        }

        public boolean isDateBased() {
            return true;
        }

        public boolean isTimeBased() {
            return false;
        }

        public boolean isSupportedBy(final TemporalAccessor temporal) {
            return temporal.isSupported(ChronoField.YEAR);
        }

        public ValueRange rangeRefinedBy(final TemporalAccessor temporal) {
            return range();
        }

        public long getFrom(final TemporalAccessor temporal) {
            return temporal.get(ChronoField.YEAR) / 100;
        }

        @SuppressWarnings("unchecked")
        public <R extends Temporal> R adjustInto(final R temporal, final long newValue) {
            return (R) temporal.with(ChronoField.YEAR, (temporal.get(ChronoField.YEAR) % 100) + 100 * newValue);
        }
    };
}
