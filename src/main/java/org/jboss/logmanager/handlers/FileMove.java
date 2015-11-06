/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2015 Red Hat, Inc., and individual contributors
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
package org.jboss.logmanager.handlers;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author panos
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class FileMove {

    /**
     * Moves the source file to the target file. The target file is first deleted if it exists, then a
     * {@linkplain File#renameTo(File) rename} is attempted. If the rename fails the source file is copied byte for byte
     * to the target finally deleting the source file in the end.
     *
     * @param src    the source file to copy
     * @param target the target file
     *
     * @throws IOException if an error occurs moving the file
     */
    public static void move(final File src, final File target) throws IOException {
        // If the target (bfile) exists, delete it first. In some cases this should allow the rename to succeed
        if (target.exists()) {
            target.delete();
        }
        // First attempt to rename the file, if that fails manually copy the file
        if (!src.renameTo(target)) {

            InputStream inStream = null;
            OutputStream outStream = null;

            try {
                inStream = new FileInputStream(src);
                outStream = new FileOutputStream(target);
                byte[] buffer = new byte[1024];

                int length;
                //copy the file content in bytes
                while ((length = inStream.read(buffer)) > 0) {
                    outStream.write(buffer, 0, length);
                }

                //delete the original file
                src.delete();
            } finally {
                safeClose(inStream);
                safeClose(outStream);
            }
        }
    }

    private static void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (Exception ignore) {
        }
    }
}
