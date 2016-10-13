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

import java.util.Iterator;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class IndentingXmlWriter implements XMLStreamWriter, XMLStreamConstants {

    private static final String SPACES = "    ";

    private final XMLStreamWriter delegate;
    private int index;
    private int state = START_DOCUMENT;
    private boolean indentEnd;

    public IndentingXmlWriter(final XMLStreamWriter delegate) {
        this.delegate = delegate;
        index = 0;
        indentEnd = false;
    }

    private void indent() throws XMLStreamException {
        final int index = this.index;
        if (index > 0) {
            for (int i = 0; i < index; i++) {
                delegate.writeCharacters(SPACES);
            }
        }

    }

    private void newline() throws XMLStreamException {
        delegate.writeCharacters("\n");
    }

    @Override
    public void writeStartElement(final String localName) throws XMLStreamException {
        newline();
        indent();
        delegate.writeStartElement(localName);
        indentEnd = false;
        state = START_ELEMENT;
        index++;
    }

    @Override
    public void writeStartElement(final String namespaceURI, final String localName) throws XMLStreamException {
        newline();
        indent();
        delegate.writeStartElement(namespaceURI, localName);
        indentEnd = false;
        state = START_ELEMENT;
        index++;
    }

    @Override
    public void writeStartElement(final String prefix, final String localName, final String namespaceURI) throws XMLStreamException {
        newline();
        indent();
        delegate.writeStartElement(prefix, localName, namespaceURI);
        indentEnd = false;
        state = START_ELEMENT;
        index++;
    }

    @Override
    public void writeEmptyElement(final String namespaceURI, final String localName) throws XMLStreamException {
        newline();
        indent();
        delegate.writeEmptyElement(namespaceURI, localName);
        state = END_ELEMENT;
    }

    @Override
    public void writeEmptyElement(final String prefix, final String localName, final String namespaceURI) throws XMLStreamException {
        newline();
        indent();
        delegate.writeEmptyElement(prefix, localName, namespaceURI);
        state = END_ELEMENT;
    }

    @Override
    public void writeEmptyElement(final String localName) throws XMLStreamException {
        newline();
        indent();
        delegate.writeEmptyElement(localName);
        state = END_ELEMENT;
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        index--;
        if (state != CHARACTERS || indentEnd) {
            newline();
            indent();
            indentEnd = false;
        }
        delegate.writeEndElement();
        state = END_ELEMENT;
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        delegate.writeEndDocument();
        state = END_DOCUMENT;
    }

    @Override
    public void close() throws XMLStreamException {
        delegate.close();
    }

    @Override
    public void flush() throws XMLStreamException {
        delegate.flush();
    }

    @Override
    public void writeAttribute(final String localName, final String value) throws XMLStreamException {
        delegate.writeAttribute(localName, value);
    }

    @Override
    public void writeAttribute(final String prefix, final String namespaceURI, final String localName, final String value) throws XMLStreamException {
        delegate.writeAttribute(prefix, namespaceURI, localName, value);
    }

    @Override
    public void writeAttribute(final String namespaceURI, final String localName, final String value) throws XMLStreamException {
        delegate.writeAttribute(namespaceURI, localName, value);
    }

    @Override
    public void writeNamespace(final String prefix, final String namespaceURI) throws XMLStreamException {
        delegate.writeNamespace(prefix, namespaceURI);
    }

    @Override
    public void writeDefaultNamespace(final String namespaceURI) throws XMLStreamException {
        delegate.writeDefaultNamespace(namespaceURI);
    }

    @Override
    public void writeComment(final String data) throws XMLStreamException {
        newline();
        indent();
        delegate.writeComment(data);
        state = COMMENT;
    }

    @Override
    public void writeProcessingInstruction(final String target) throws XMLStreamException {
        newline();
        indent();
        delegate.writeProcessingInstruction(target);
        state = PROCESSING_INSTRUCTION;
    }

    @Override
    public void writeProcessingInstruction(final String target, final String data) throws XMLStreamException {
        newline();
        indent();
        delegate.writeProcessingInstruction(target, data);
        state = PROCESSING_INSTRUCTION;
    }

    @Override
    public void writeCData(final String data) throws XMLStreamException {
        delegate.writeCData(data);
        state = CDATA;
    }

    @Override
    public void writeDTD(final String dtd) throws XMLStreamException {
        newline();
        indent();
        delegate.writeDTD(dtd);
        state = DTD;
    }

    @Override
    public void writeEntityRef(final String name) throws XMLStreamException {
        delegate.writeEntityRef(name);
        state = ENTITY_REFERENCE;
    }

    @Override
    public void writeStartDocument() throws XMLStreamException {
        delegate.writeStartDocument();
        newline();
        state = START_DOCUMENT;
    }

    @Override
    public void writeStartDocument(final String version) throws XMLStreamException {
        delegate.writeStartDocument(version);
        newline();
        state = START_DOCUMENT;
    }

    @Override
    public void writeStartDocument(final String encoding, final String version) throws XMLStreamException {
        delegate.writeStartDocument(encoding, version);
        newline();
        state = START_DOCUMENT;
    }

    @Override
    public void writeCharacters(final String text) throws XMLStreamException {
        indentEnd = false;
        boolean first = true;
        final Iterator<String> iterator = Splitter.iterator(text, '\n');
        while (iterator.hasNext()) {
            final String t = iterator.next();
            // On first iteration if more than one line is required, skip to a new line and indent
            if (first && iterator.hasNext()) {
                first = false;
                newline();
                indent();
            }
            delegate.writeCharacters(t);
            if (iterator.hasNext()) {
                newline();
                indent();
                indentEnd = true;
            }
        }
        state = CHARACTERS;
    }

    @Override
    public void writeCharacters(final char[] text, final int start, final int len) throws XMLStreamException {
        delegate.writeCharacters(text, start, len);
    }

    @Override
    public String getPrefix(final String uri) throws XMLStreamException {
        return delegate.getPrefix(uri);
    }

    @Override
    public void setPrefix(final String prefix, final String uri) throws XMLStreamException {
        delegate.setPrefix(prefix, uri);
    }

    @Override
    public void setDefaultNamespace(final String uri) throws XMLStreamException {
        delegate.setDefaultNamespace(uri);
    }

    @Override
    public void setNamespaceContext(final NamespaceContext context) throws XMLStreamException {
        delegate.setNamespaceContext(context);
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        return delegate.getNamespaceContext();
    }

    @Override
    public Object getProperty(final String name) throws IllegalArgumentException {
        return delegate.getProperty(name);
    }
}
