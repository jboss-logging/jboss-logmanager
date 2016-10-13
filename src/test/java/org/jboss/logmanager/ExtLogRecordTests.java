/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import junit.framework.Assert;
import org.jboss.logmanager.ExtLogRecord.AttachmentKey;
import org.jboss.logmanager.ExtLogRecord.FormatStyle;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Test
public class ExtLogRecordTests {

    public void serializationTest() throws Exception {
        ExtLogRecord record = new ExtLogRecord(Level.INFO, "This is a test message", FormatStyle.NO_FORMAT, "org.jboss.logmanager.test");
        final AttachmentKey<String> key = AttachmentKey.create(String.class);
        final String value = "AttachmentValue";
        record.attach(key, value);

        final AttachmentKey<StringHolder> stringHolderKey = AttachmentKey.create(StringHolder.class);
        final StringHolder stringHolder = new StringHolder("StringHolder");
        record.attach(stringHolderKey, stringHolder);

        // Serialize the object
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ObjectOutputStream objOut = new ObjectOutputStream(out);
        try {
            objOut.writeObject(record);
        } finally {
            objOut.close();
        }

        // Read the object
        ExtLogRecord deserializedRecord;
        final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        final ObjectInputStream objIn = new ObjectInputStream(in);
        try {
            deserializedRecord = (ExtLogRecord) objIn.readObject();
        } finally {
            in.close();
        }

        // Shouldn't be null
        Assert.assertNotNull(deserializedRecord);

        // Check the attachments
        Assert.assertEquals(record.getAttachment(key), deserializedRecord.getAttachment(key));
        Assert.assertEquals(record.getAttachment(stringHolderKey), deserializedRecord.getAttachment(stringHolderKey));
    }

    static class StringHolder implements Serializable {
        private static final long serialVersionUID = -1122952318343316925L;
        final String value;

        StringHolder(final String value) {
            this.value = value;
        }

        @Override
        public int hashCode() {
            return 31 * (17 + (value == null ? 0 : value.hashCode()));
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof StringHolder)) {
                return false;
            }
            final StringHolder other = (StringHolder) obj;
            return (value == null ? other.value == null : value.equals(other.value));
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
