/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.ErrorManager;

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.WithByteman;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@WithByteman
public class PeriodicSizeRotatingFileHandlerFailureTests extends AbstractHandlerTest {
    private final static String FILENAME = "rotating-file-handler.log";

    private final File logFile = new File(BASE_LOG_DIR, FILENAME);

    @Test
    @BMRule(name = "Test failed rotated", targetClass = "java.nio.file.Files", targetMethod = "move", targetLocation = "AT ENTRY", condition = "$2.getFileName().toString().equals(\"rotating-file-handler.log.2\")", action = "throw new IOException(\"Fail on purpose\")")
    public void testFailedRotate() throws Exception {
        final PeriodicSizeRotatingFileHandler handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setErrorManager(AssertingErrorManager.of(ErrorManager.GENERIC_FAILURE));
        handler.setRotateSize(1024L);
        handler.setMaxBackupIndex(5);
        handler.setFile(logFile);

        // Allow a few rotates
        for (int i = 0; i < 100; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // The log file should exist, as should one rotated file since we fail the rotation on the second rotate
        Assertions.assertTrue(logFile.exists(), () -> String.format("Expected log file %s to exist", logFile));
        final Path rotatedFile = BASE_LOG_DIR.toPath().resolve(FILENAME + ".1");
        Assertions.assertTrue(Files.exists(rotatedFile), () -> String.format("Expected rotated file %s to exist", rotatedFile));

        // The last line of the log file should end with "99" as it should be the last record
        final List<String> lines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
        final String lastLine = lines.get(lines.size() - 1);
        Assertions.assertTrue(lastLine.endsWith("99"), "Expected the last line to end with 99: " + lastLine);
    }
}
