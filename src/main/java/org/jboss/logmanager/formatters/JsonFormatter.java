/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonValue;
import javax.json.stream.JsonGeneratorFactory;

/**
 * A formatter that outputs the record into JSON format optionally printing details.
 * <p/>
 * Note that including details can be expensive in terms of calculating the caller.
 * <p/>
 * The details include;
 * <ul>
 * <li>{@link org.jboss.logmanager.ExtLogRecord#getSourceClassName() source class name}</li>
 * <li>{@link org.jboss.logmanager.ExtLogRecord#getSourceFileName() source file name}</li>
 * <li>{@link org.jboss.logmanager.ExtLogRecord#getSourceMethodName() source method name}</li>
 * <li>{@link org.jboss.logmanager.ExtLogRecord#getSourceLineNumber() source line number}</li>
 * </ul>
 * <p/>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JsonFormatter extends StructuredFormatter {

    private final Map<String, Object> config;

    /**
     * Creates a new JSON formatter.
     */
    public JsonFormatter() {
        this(false);
    }

    /**
     * Creates a new JSON formatter.
     *
     * @param printDetails {@code true} to print the details
     */
    public JsonFormatter(final boolean printDetails) {
        super(printDetails);
        config = new HashMap<>();
    }

    /**
     * Indicates whether or not pretty printing is enabled.
     *
     * @return {@code true} if pretty printing is enabled, otherwise {@code false}
     */
    public boolean isPrettyPrint() {
        synchronized (config) {
            return (config.containsKey(javax.json.stream.JsonGenerator.PRETTY_PRINTING) ? (Boolean) config.get(javax.json.stream.JsonGenerator.PRETTY_PRINTING) : false);
        }
    }

    /**
     * Turns on or off pretty printing.
     *
     * @param b {@code true} to turn on pretty printing or {@code false} to turn it off
     */
    public void setPrettyPrint(final boolean b) {
        synchronized (config) {
            if (b) {
                config.put(javax.json.stream.JsonGenerator.PRETTY_PRINTING, true);
            } else {
                config.remove(javax.json.stream.JsonGenerator.PRETTY_PRINTING);
            }
        }
    }

    @Override
    protected Generator createGenerator() {
        return new JsonGenerator();
    }

    private class JsonGenerator extends Generator {
        private final javax.json.stream.JsonGenerator generator;
        private final StringBuilder sb;

        private JsonGenerator() {
            sb = new StringBuilder();
            final Map<String, Object> config;
            synchronized (JsonFormatter.this.config) {
                config = new HashMap<>(JsonFormatter.this.config);
            }
            final JsonGeneratorFactory factory = Json.createGeneratorFactory(config);
            generator = factory.createGenerator(new StringBuilderWriter(sb));
        }

        @Override
        public Generator begin() {
            generator.writeStartObject();
            return this;
        }

        @Override
        public Generator add(final String key, final int value) {
            generator.write(key, value);
            return this;
        }

        @Override
        public Generator add(final String key, final long value) {
            generator.write(key, value);
            return this;
        }

        @Override
        public Generator add(final String key, final String valueKey, final Object[] value) {
            generator.writeStartArray(key);
            if (value != null) {
                for (Object o : value) {
                    writeObject(null, o);
                }
            }
            generator.writeEnd();
            return this;
        }

        @Override
        public Generator add(final String key, final Map<String, ?> value) {
            generator.writeStartObject(key);
            if (value != null) {
                for (Map.Entry<String, ?> entry : value.entrySet()) {
                    writeObject(entry.getKey(), entry.getValue());
                }
            }
            generator.writeEnd();
            return this;
        }

        @Override
        public Generator add(final String key, final String value) {
            if (value == null) {
                generator.writeNull(key);
            } else {
                generator.write(key, value);
            }
            return this;
        }

        @Override
        public Generator addStackTrace(final Throwable throwable) throws Exception {
            generator.writeStartObject(Keys.EXCEPTION);
            add(Keys.EXCEPTION_MESSAGE, throwable.getMessage());

            generator.writeStartArray(Keys.EXCEPTION_FRAMES);
            final StackTraceElement[] elements = throwable.getStackTrace();
            for (StackTraceElement e : elements) {
                generator.writeStartObject();
                add(Keys.EXCEPTION_FRAME_CLASS, e.getClassName());
                add(Keys.EXCEPTION_FRAME_METHOD, e.getMethodName());
                final int line = e.getLineNumber();
                if (line >= 0) {
                    add(Keys.EXCEPTION_FRAME_LINE, e.getLineNumber());
                }
                generator.writeEnd(); // end exception element
            }
            generator.writeEnd(); // end array

            generator.writeEnd(); // end exception
            return this;
        }

        @Override
        public String build() {
            generator.writeEnd(); // end record
            generator.flush();
            generator.close();
            return sb.toString();
        }

        private void writeObject(final String key, final Object obj) {
            if (obj == null) {
                if (key == null) {
                    generator.writeNull();
                } else {
                    generator.writeNull(key);
                }
            } else if (obj instanceof Boolean) {
                final Boolean value = (Boolean) obj;
                if (key == null) {
                    generator.write(value);
                } else {
                    generator.write(key, value);
                }
            } else if (obj instanceof Integer) {
                final Integer value = (Integer) obj;
                if (key == null) {
                    generator.write(value);
                } else {
                    generator.write(key, value);
                }
            } else if (obj instanceof Long) {
                final Long value = (Long) obj;
                if (key == null) {
                    generator.write(value);
                } else {
                    generator.write(key, value);
                }
            } else if (obj instanceof Double) {
                final Double value = (Double) obj;
                if (key == null) {
                    generator.write(value);
                } else {
                    generator.write(key, value);
                }
            } else if (obj instanceof BigInteger) {
                final BigInteger value = (BigInteger) obj;
                if (key == null) {
                    generator.write(value);
                } else {
                    generator.write(key, value);
                }
            } else if (obj instanceof BigDecimal) {
                final BigDecimal value = (BigDecimal) obj;
                if (key == null) {
                    generator.write(value);
                } else {
                    generator.write(key, value);
                }
            } else if (obj instanceof String) {
                final String value = (String) obj;
                if (key == null) {
                    generator.write(value);
                } else {
                    generator.write(key, value);
                }
            } else if (obj instanceof JsonValue) {
                final JsonValue value = (JsonValue) obj;
                if (key == null) {
                    generator.write(value);
                } else {
                    generator.write(key, value);
                }
            } else {
                final String value = String.valueOf(obj);
                if (key == null) {
                    generator.write(value);
                } else {
                    generator.write(key, value);
                }
            }
        }
    }
}
