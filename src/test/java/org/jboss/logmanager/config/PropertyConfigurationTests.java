/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.filters.AcceptAllFilter;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.QueueHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PropertyConfigurationTests {

    private static final Map<String, String> DEFAULT_VALUES = new HashMap<>();
    private static final String HANDLER_NAME = "typeHandler";
    private static final String CHILD_HANDLER_NAME = "queueHandler";
    private static final String PATTERN_FORMATTER_NAME = "PATTERN";
    private static final String PATTERN = "%d{MM-dd-yyyy'T'HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n";
    private static final String ERROR_MANAGER_NAME = "testErrorManager";
    private static final String LOGGER_NAME = PropertyConfigurationTests.class.getName();

    @BeforeClass
    public static void setupDefaults() {
        DEFAULT_VALUES.put("stringValue", "stringValue");
        DEFAULT_VALUES.put("handler", CHILD_HANDLER_NAME);
        DEFAULT_VALUES.put("filter", "accept");
        DEFAULT_VALUES.put("formatter", PATTERN_FORMATTER_NAME);
        DEFAULT_VALUES.put("errorManager", ERROR_MANAGER_NAME);
        DEFAULT_VALUES.put("level", "WARN");
        DEFAULT_VALUES.put("logger", LOGGER_NAME);
        DEFAULT_VALUES.put("booleanValue", "true");
        DEFAULT_VALUES.put("booleanTypeValue", "TRUE");
        DEFAULT_VALUES.put("byteValue", "65"); // Letter A
        DEFAULT_VALUES.put("byteTypeValue", "66"); // Letter B
        DEFAULT_VALUES.put("shortValue", "33");
        DEFAULT_VALUES.put("shortTypeValue", "66");
        DEFAULT_VALUES.put("intValue", "133");
        DEFAULT_VALUES.put("intTypeValue", "166");
        DEFAULT_VALUES.put("longValue", "233");
        DEFAULT_VALUES.put("longTypeValue", "266");
        DEFAULT_VALUES.put("floatValue", "33.33");
        DEFAULT_VALUES.put("floatTypeValue", "66.66");
        DEFAULT_VALUES.put("doubleValue", "133.33");
        DEFAULT_VALUES.put("doubleTypeValue", "166.66");
        DEFAULT_VALUES.put("charValue", "c");
        DEFAULT_VALUES.put("charTypeValue", "d");
        DEFAULT_VALUES.put("timeZoneValue", "GMT");
        DEFAULT_VALUES.put("charsetValue", "UTF-8");
        DEFAULT_VALUES.put("enumValue", "VALUE1");
    }

    private LogContextConfiguration logContextConfiguration;
    private HandlerConfiguration handlerConfiguration;

    @Before
    public void setupHandlerConfig() {
        final LogContext logContext = LogContext.create();
        logContextConfiguration = LogContextConfiguration.Factory.create(logContext);
        handlerConfiguration = logContextConfiguration.addHandlerConfiguration(null, TypeHandler.class.getName(), HANDLER_NAME);
        // Create the child handler,  formatter and error manager
        logContextConfiguration.addHandlerConfiguration(null, QueueHandler.class.getName(), CHILD_HANDLER_NAME);
        final FormatterConfiguration formatterConfiguration = logContextConfiguration.addFormatterConfiguration(null,
                PatternFormatter.class.getName(), PATTERN_FORMATTER_NAME, "pattern");
        formatterConfiguration.setPropertyValueString("pattern", PATTERN);
        logContextConfiguration.addErrorManagerConfiguration(null, ErrorManager.class.getName(), ERROR_MANAGER_NAME);
        setAllValues(handlerConfiguration);
    }

    @Test
    public void testSetProperties() {
        // We need to commit the changes
        logContextConfiguration.commit();
        checkPropertiesSet();
    }

    @Test
    public void testSetPropertiesRollback() throws Exception {
        // Prepare the output which should set the properties, then rollback and ensure the properties were removed
        logContextConfiguration.prepare();
        checkPropertiesSet();
        logContextConfiguration.forget();
        checkPropertiesRemoved();
    }

    @Test
    public void testRemoveProperties() throws Exception {
        // We need to commit the changes
        logContextConfiguration.commit();
        // First we'll validate all the properties have been set before we remove them all
        checkPropertiesSet();
        // Remove all properties
        for (String name : DEFAULT_VALUES.keySet()) {
            Assert.assertTrue("Failed to remove property " + name, handlerConfiguration.removeProperty(name));
        }
        logContextConfiguration.commit();
        checkPropertiesRemoved();
    }

    @Test
    public void testRemovePropertiesRollback() throws Exception {
        // We need to commit the changes
        logContextConfiguration.commit();
        // Remove all properties
        for (String name : DEFAULT_VALUES.keySet()) {
            Assert.assertTrue("Failed to remove property " + name, handlerConfiguration.removeProperty(name));
        }
        logContextConfiguration.prepare();
        // Everything should be set now as prepare sets properties
        checkPropertiesRemoved();
        // Forget instead of commit so that properties should be rolled back
        logContextConfiguration.forget();
        checkPropertiesSet();
    }

    private static void checkPropertiesSet() {
        Assert.assertEquals("stringValue", TypeHandler.stringValue);
        Assert.assertEquals(QueueHandler.class, TypeHandler.handler.getClass());
        Assert.assertEquals(AcceptAllFilter.class, TypeHandler.filter.getClass());
        Assert.assertEquals(PatternFormatter.class, TypeHandler.formatter.getClass());
        Assert.assertEquals(PATTERN, ((PatternFormatter) (TypeHandler.formatter)).getPattern());
        Assert.assertEquals(ErrorManager.class, TypeHandler.errorManager.getClass());
        Assert.assertEquals(org.jboss.logmanager.Level.WARN, TypeHandler.level);
        Assert.assertEquals(LOGGER_NAME, TypeHandler.logger.getName());
        Assert.assertTrue(TypeHandler.booleanValue);
        Assert.assertEquals(Boolean.TRUE, TypeHandler.booleanTypeValue);
        Assert.assertEquals(65, TypeHandler.byteValue);
        Assert.assertEquals(Byte.valueOf((byte) 66), TypeHandler.byteTypeValue);
        Assert.assertEquals((short) 33, TypeHandler.shortValue);
        Assert.assertEquals(Short.valueOf((short) 66), TypeHandler.shortTypeValue);
        Assert.assertEquals(133, TypeHandler.intValue);
        Assert.assertEquals(Integer.valueOf(166), TypeHandler.intTypeValue);
        Assert.assertEquals(233L, TypeHandler.longValue);
        Assert.assertEquals(Long.valueOf(266L), TypeHandler.longTypeValue);
        Assert.assertTrue("Expected 33.33f but found " + Float.toString(TypeHandler.floatValue), (Float.compare(33.33f, TypeHandler.floatValue) == 0));
        Assert.assertEquals(Float.valueOf(66.66f), TypeHandler.floatTypeValue);
        Assert.assertTrue("Expected 133.33d but found " + Double.toString(TypeHandler.doubleValue), (Double.compare(133.33d, TypeHandler.doubleValue) == 0));
        Assert.assertEquals(Double.valueOf(166.66d), TypeHandler.doubleTypeValue);
        Assert.assertEquals('c', TypeHandler.charValue);
        Assert.assertEquals(Character.valueOf('d'), TypeHandler.charTypeValue);
        Assert.assertEquals(TimeZone.getTimeZone("GMT"), TypeHandler.timeZoneValue);
        Assert.assertEquals(StandardCharsets.UTF_8, TypeHandler.charsetValue);
        Assert.assertEquals(TypeHandler.TestEnum.VALUE1, TypeHandler.enumValue);
    }

    private static void checkPropertiesRemoved() {
        Assert.assertNull(TypeHandler.stringValue);
        Assert.assertNull(TypeHandler.handler);
        Assert.assertNull(TypeHandler.filter);
        Assert.assertNull(TypeHandler.formatter);
        Assert.assertNull(TypeHandler.errorManager);
        Assert.assertNull(TypeHandler.level);
        Assert.assertNull(TypeHandler.logger);
        Assert.assertFalse(TypeHandler.booleanValue);
        Assert.assertNull(TypeHandler.booleanTypeValue);
        Assert.assertEquals(0x00, TypeHandler.byteValue);
        Assert.assertNull(TypeHandler.byteTypeValue);
        Assert.assertEquals((short) 0, TypeHandler.shortValue);
        Assert.assertNull(TypeHandler.shortTypeValue);
        Assert.assertEquals(0, TypeHandler.intValue);
        Assert.assertNull(TypeHandler.intTypeValue);
        Assert.assertEquals(0L, TypeHandler.longValue);
        Assert.assertNull(TypeHandler.longTypeValue);
        Assert.assertTrue("Expected 0.0f but found " + Float.toString(TypeHandler.floatValue), (Float.compare(0.0f, TypeHandler.floatValue) == 0));
        Assert.assertNull(TypeHandler.floatTypeValue);
        Assert.assertTrue("Expected 0.0d but found " + Double.toString(TypeHandler.doubleValue), (Double.compare(0.0d, TypeHandler.doubleValue) == 0));
        Assert.assertNull(TypeHandler.doubleTypeValue);
        Assert.assertEquals(0x00, TypeHandler.charValue);
        Assert.assertNull(TypeHandler.charTypeValue);
        Assert.assertNull(TypeHandler.timeZoneValue);
        Assert.assertNull(TypeHandler.charsetValue);
        Assert.assertNull(TypeHandler.enumValue);
    }

    private static void setAllValues(final HandlerConfiguration handlerConfiguration) {
        for (Map.Entry<String, String> entry : DEFAULT_VALUES.entrySet()) {
            handlerConfiguration.setPropertyValueString(entry.getKey(), entry.getValue());
        }
    }


    @SuppressWarnings("unused")
    public static class TypeHandler extends ExtHandler {
        public enum TestEnum {
            VALUE1
        }

        static String stringValue = null;
        static Handler handler = null;
        static Filter filter = null;
        static Formatter formatter = null;
        static ErrorManager errorManager = null;
        static Level level = null;
        static Logger logger = null;
        static boolean booleanValue = false;
        static Boolean booleanTypeValue = null;
        static byte byteValue = 0x00;
        static Byte byteTypeValue = null;
        static short shortValue = -1;
        static Short shortTypeValue = null;
        static int intValue = -1;
        static Integer intTypeValue = null;
        static long longValue = -1L;
        static Long longTypeValue = null;
        static float floatValue = -1f;
        static Float floatTypeValue = null;
        static double doubleValue = -1D;
        static Double doubleTypeValue = null;
        static char charValue = 0x00;
        static Character charTypeValue = null;
        static TimeZone timeZoneValue = null;
        static Charset charsetValue = null;
        static TestEnum enumValue = null;

        public String getStringValue() {
            return stringValue;
        }

        public void setStringValue(final String stringValue) {
            TypeHandler.stringValue = stringValue;
        }

        public Handler getHandler() {
            return handler;
        }

        public void setHandler(final Handler handler) {
            TypeHandler.handler = handler;
        }

        @Override
        public Filter getFilter() {
            return filter;
        }

        @Override
        public void setFilter(final Filter filter) {
            TypeHandler.filter = filter;
        }

        @Override
        public Formatter getFormatter() {
            return formatter;
        }

        @Override
        public void setFormatter(final Formatter formatter) {
            TypeHandler.formatter = formatter;
        }

        @Override
        public ErrorManager getErrorManager() {
            return errorManager;
        }

        @Override
        public void setErrorManager(final ErrorManager errorManager) {
            TypeHandler.errorManager = errorManager;
        }

        @Override
        public Level getLevel() {
            return level;
        }

        @Override
        public void setLevel(final Level level) {
            TypeHandler.level = level;
        }

        public Logger getLogger() {
            return logger;
        }

        public void setLogger(final Logger logger) {
            TypeHandler.logger = logger;
        }

        public boolean isBooleanValue() {
            return booleanValue;
        }

        public void setBooleanValue(final boolean booleanValue) {
            TypeHandler.booleanValue = booleanValue;
        }

        public Boolean getBooleanTypeValue() {
            return booleanTypeValue;
        }

        public void setBooleanTypeValue(final Boolean booleanTypeValue) {
            TypeHandler.booleanTypeValue = booleanTypeValue;
        }

        public byte getByteValue() {
            return byteValue;
        }

        public void setByteValue(final byte byteValue) {
            TypeHandler.byteValue = byteValue;
        }

        public Byte getByteTypeValue() {
            return byteTypeValue;
        }

        public void setByteTypeValue(final Byte byteTypeValue) {
            TypeHandler.byteTypeValue = byteTypeValue;
        }

        public short getShortValue() {
            return shortValue;
        }

        public void setShortValue(final short shortValue) {
            TypeHandler.shortValue = shortValue;
        }

        public Short getShortTypeValue() {
            return shortTypeValue;
        }

        public void setShortTypeValue(final Short shortTypeValue) {
            TypeHandler.shortTypeValue = shortTypeValue;
        }

        public int getIntValue() {
            return intValue;
        }

        public void setIntValue(final int intValue) {
            TypeHandler.intValue = intValue;
        }

        public Integer getIntTypeValue() {
            return intTypeValue;
        }

        public void setIntTypeValue(final Integer intTypeValue) {
            TypeHandler.intTypeValue = intTypeValue;
        }

        public long getLongValue() {
            return longValue;
        }

        public void setLongValue(final long longValue) {
            TypeHandler.longValue = longValue;
        }

        public Long getLongTypeValue() {
            return longTypeValue;
        }

        public void setLongTypeValue(final Long longTypeValue) {
            TypeHandler.longTypeValue = longTypeValue;
        }

        public float getFloatValue() {
            return floatValue;
        }

        public void setFloatValue(final float floatValue) {
            TypeHandler.floatValue = floatValue;
        }

        public Float getFloatTypeValue() {
            return floatTypeValue;
        }

        public void setFloatTypeValue(final Float floatTypeValue) {
            TypeHandler.floatTypeValue = floatTypeValue;
        }

        public double getDoubleValue() {
            return doubleValue;
        }

        public void setDoubleValue(final double doubleValue) {
            TypeHandler.doubleValue = doubleValue;
        }

        public Double getDoubleTypeValue() {
            return doubleTypeValue;
        }

        public void setDoubleTypeValue(final Double doubleTypeValue) {
            TypeHandler.doubleTypeValue = doubleTypeValue;
        }

        public char getCharValue() {
            return charValue;
        }

        public void setCharValue(final char charValue) {
            TypeHandler.charValue = charValue;
        }

        public Character getCharTypeValue() {
            return charTypeValue;
        }

        public void setCharTypeValue(final Character charTypeValue) {
            TypeHandler.charTypeValue = charTypeValue;
        }

        public TimeZone getTimeZoneValue() {
            return timeZoneValue;
        }

        public void setTimeZoneValue(final TimeZone timeZoneValue) {
            TypeHandler.timeZoneValue = timeZoneValue;
        }

        public static Charset getCharsetValue() {
            return charsetValue;
        }

        public static void setCharsetValue(final Charset charsetValue) {
            TypeHandler.charsetValue = charsetValue;
        }

        public TestEnum getEnumValue() {
            return enumValue;
        }

        public void setEnumValue(final TestEnum enumValue) {
            TypeHandler.enumValue = enumValue;
        }
    }
}
