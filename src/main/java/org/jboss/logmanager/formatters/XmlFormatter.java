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

import java.util.Map;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * A formatter that outputs the record in XML format.
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
public class XmlFormatter extends StructuredFormatter {

    private volatile boolean prettyPrint;

    public XmlFormatter() {
        this(false);
    }

    public XmlFormatter(final boolean printDetails) {
        super(printDetails);
    }

    /**
     * Indicates whether or not pretty printing is enabled.
     *
     * @return {@code true} if pretty printing is enabled, otherwise {@code false}
     */
    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    /**
     * Turns on or off pretty printing.
     *
     * @param b {@code true} to turn on pretty printing or {@code false} to turn it off
     */
    public void setPrettyPrint(final boolean b) {
        prettyPrint = b;
    }

    @Override
    protected Generator createGenerator() throws Exception {
        return new XmlGenerator();
    }

    private class XmlGenerator extends Generator {
        private final StringBuilder sb;
        private final XMLStreamWriter xmlWriter;

        private XmlGenerator() throws XMLStreamException {
            sb = new StringBuilder();
            final StringBuilderWriter writer = new StringBuilderWriter(sb);
            final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
            if (prettyPrint) {
                xmlWriter = new IndentingXmlWriter(xmlOutputFactory.createXMLStreamWriter(writer));
            } else {
                xmlWriter = xmlOutputFactory.createXMLStreamWriter(writer);
            }
        }

        @Override
        public Generator begin() throws Exception {
            writeStart(Keys.RECORD_KEY);
            return this;
        }

        @Override
        public Generator add(final String key, final String valueKey, final Object[] value) throws Exception {
            if (value == null) {
                writeEmpty(key);
            } else {
                writeStart(key);
                for (Object o : value) {
                    if (o == null) {
                        writeEmpty(valueKey);
                    } else {
                        add(valueKey, String.valueOf(o));
                    }
                }
                writeEnd();
            }
            return this;
        }

        @Override
        public Generator add(final String key, final Map<String, ?> value) throws Exception {
            if (value == null) {
                writeEmpty(key);
            } else {
                writeStart(key);
                for (Map.Entry<String, ?> entry : value.entrySet()) {
                    final String k = entry.getKey();
                    final Object v = entry.getValue();
                    if (v == null) {
                        writeEmpty(k);
                    } else {
                        add(k, String.valueOf(v));
                    }
                }
                writeEnd();
            }
            return this;
        }

        @Override
        public Generator add(final String key, final String value) throws Exception {
            if (value == null) {
                writeEmpty(key);
            } else {
                writeStart(key);
                xmlWriter.writeCharacters(value);
                writeEnd();
            }
            return this;
        }

        @Override
        public Generator addStackTrace(final Throwable throwable) throws Exception {
            writeStart(Keys.EXCEPTION);
            add(Keys.EXCEPTION_MESSAGE, throwable.getMessage());

            final StackTraceElement[] elements = throwable.getStackTrace();
            for (StackTraceElement e : elements) {
                writeStart(Keys.EXCEPTION_FRAME);
                add(Keys.EXCEPTION_FRAME_CLASS, e.getClassName());
                add(Keys.EXCEPTION_FRAME_METHOD, e.getMethodName());
                final int line = e.getLineNumber();
                if (line >= 0) {
                    add(Keys.EXCEPTION_FRAME_LINE, e.getLineNumber());
                }
                writeEnd(); // end exception element
            }

            writeEnd(); // end exception

            return this;
        }

        @Override
        public String build() throws Exception {
            writeEnd(); // end record
            safeFlush(xmlWriter);
            safeClose(xmlWriter);
            return sb.toString();
        }

        private void writeEmpty(final String name) throws XMLStreamException {
            xmlWriter.writeEmptyElement(name);
        }

        private void writeStart(final String name) throws XMLStreamException {
            xmlWriter.writeStartElement(name);
        }

        private void writeEnd() throws XMLStreamException {
            xmlWriter.writeEndElement();
        }

        public void safeFlush(final XMLStreamWriter flushable) {
            if (flushable != null) try {
                flushable.flush();
            } catch (Throwable ignore) {
            }
        }

        public void safeClose(final XMLStreamWriter closeable) {
            if (closeable != null) try {
                closeable.close();
            } catch (Throwable ignore) {
            }
        }
    }
}
