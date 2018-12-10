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

package org.jboss.logmanager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.jboss.logmanager.formatters.JsonFormatter;
import org.jboss.logmanager.handlers.FileHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SystemLoggerTests {
    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

    private Path stdout;
    private Path configFile;
    private Path logFile;

    @Before
    public void setup() throws Exception {
        stdout = Files.createTempFile("stdout", ".txt");
        logFile = Files.createTempFile("system-logger", ".log");
        configFile = Files.createTempFile("logging", ".properties");
    }

    @After
    public void killProcess() throws IOException {
        Files.deleteIfExists(stdout);
        Files.deleteIfExists(logFile);
        Files.deleteIfExists(configFile);
    }

    @Test
    public void testSystemLoggerActivated() throws Exception {
        createJBossLoggingConfig();
        final Process process = createProcess("-Dlogging.configuration=" + configFile.toUri().toURL());
        if (process.waitFor(3L, TimeUnit.SECONDS)) {
            final int exitCode = process.exitValue();
            final StringBuilder msg = new StringBuilder("Expected exit value 0 got ")
                    .append(exitCode);
            appendStdout(msg);
            Assert.assertEquals(msg.toString(), 0, exitCode);
        } else {
            final Process destroyed = process.destroyForcibly();
            final StringBuilder msg = new StringBuilder("Failed to exit process within 3 seconds. Exit Code: ")
                    .append(destroyed.exitValue());
            appendStdout(msg);
            Assert.fail(msg.toString());
        }

        final JsonObject json = readLogFile(logFile);
        final JsonArray lines = json.getJsonArray("lines");
        Assert.assertEquals(2, lines.size());
        // The first line should be from a SystemLogger
        JsonObject line = lines.getJsonObject(0);
        Assert.assertEquals("org.jboss.logmanager.JBossLoggerFinder$JBossSystemLogger", line.getString("loggerClassName"));
        JsonObject mdc = line.getJsonObject("mdc");
        Assert.assertEquals("org.jboss.logmanager.JBossLoggerFinder$JBossSystemLogger", mdc.getString("logger.type"));
        Assert.assertEquals(LogManager.class.getName(), mdc.getString("java.util.logging.LogManager"));
        Assert.assertEquals(LogManager.class.getName(), mdc.getString("java.util.logging.manager"));

        // The second line should be from a JUL logger
        line = lines.getJsonObject(1);
        Assert.assertEquals(Logger.class.getName(), line.getString("loggerClassName"));
        mdc = line.getJsonObject("mdc");
        Assert.assertEquals(Logger.class.getName(), mdc.getString("logger.type"));
        Assert.assertEquals(LogManager.class.getName(), mdc.getString("java.util.logging.LogManager"));
        Assert.assertEquals(LogManager.class.getName(), mdc.getString("java.util.logging.manager"));
    }

    @Test
    public void testSystemLoggerAccessedBeforeActivated() throws Exception {
        final Process process = createProcess("-Dsystem.logger.test.jul=true", "-Dtest.log.file.name=" + logFile.toString(),
                "-Djava.util.logging.config.class=" + JulLoggingConfigurator.class.getName());
        if (process.waitFor(3L, TimeUnit.SECONDS)) {
            final int exitCode = process.exitValue();
            final StringBuilder msg = new StringBuilder("Expected exit value 0 got ")
                    .append(exitCode);
            appendStdout(msg);
            Assert.assertEquals(msg.toString(), 0, exitCode);
        } else {
            final Process destroyed = process.destroyForcibly();
            final StringBuilder msg = new StringBuilder("Failed to exit process within 3 seconds. Exit Code: ")
                    .append(destroyed.exitValue());
            appendStdout(msg);
            Assert.fail(msg.toString());
        }

        final JsonObject json = readLogFile(logFile);
        final JsonArray lines = json.getJsonArray("lines");
        Assert.assertEquals(3, lines.size());

        // The first line should be an error indicating the java.util.logging.manager wasn't set before the LogManager
        // was accessed
        JsonObject line = lines.getJsonObject(0);
        Assert.assertEquals("ERROR", line.getString("level"));
        final String message = line.getString("message");
        Assert.assertNotNull(message);
        Assert.assertTrue(message.contains("java.util.logging.manager"));

        // The second line should be from a SystemLogger
        line = lines.getJsonObject(1);
        Assert.assertEquals("org.jboss.logmanager.JBossLoggerFinder$JBossSystemLogger", line.getString("loggerClassName"));
        JsonObject mdc = line.getJsonObject("mdc");
        Assert.assertEquals("org.jboss.logmanager.JBossLoggerFinder$JBossSystemLogger", mdc.getString("logger.type"));
        Assert.assertEquals("java.util.logging.LogManager", mdc.getString("java.util.logging.LogManager"));

        // The third line should be from a JUL logger
        line = lines.getJsonObject(2);
        Assert.assertEquals("java.util.logging.Logger", line.getString("loggerClassName"));
        mdc = line.getJsonObject("mdc");
        Assert.assertEquals("java.util.logging.Logger", mdc.getString("logger.type"));
        Assert.assertEquals("java.util.logging.LogManager", mdc.getString("java.util.logging.LogManager"));
    }

    private Process createProcess(final String... javaOpts) throws IOException {
        final List<String> cmd = new ArrayList<>();
        cmd.add(findJavaCommand());
        cmd.add("-ea");
        cmd.add("--add-modules=java.se");
        Collections.addAll(cmd, javaOpts);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(SystemLoggerMain.class.getName());
        return new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(stdout.toFile())
                .start();
    }

    private void createJBossLoggingConfig() throws IOException {
        final Properties properties = new Properties();

        properties.setProperty("logger.level", "INFO");
        properties.setProperty("logger.handlers", "FILE");

        properties.setProperty("handler.FILE", FileHandler.class.getName());
        properties.setProperty("handler.FILE.formatter", "JSON");
        properties.setProperty("handler.FILE.level", "INFO");
        properties.setProperty("handler.FILE.properties", "autoFlush,append,fileName");
        properties.setProperty("handler.FILE.constructorProperties", "fileName,append");
        properties.setProperty("handler.FILE.append", "false");
        properties.setProperty("handler.FILE.fileName", logFile.toString());

        properties.setProperty("formatter.JSON", JsonFormatter.class.getName());

        try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            properties.store(writer, "Test logging properties");
        }
    }

    private void appendStdout(final StringBuilder builder) throws IOException {
        for (String line : Files.readAllLines(stdout)) {
            builder.append(System.lineSeparator())
                    .append(line);
        }
    }

    private static JsonObject readLogFile(final Path logFile) throws IOException {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try (JsonReader jsonReader = Json.createReader(new StringReader(line))) {
                    builder.add(jsonReader.read());
                }
            }
        }
        return Json.createObjectBuilder().add("lines", builder).build();
    }

    private static String findJavaCommand() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            String exe = "java";
            if (WINDOWS) {
                exe = "java.exe";
            }
            return Paths.get(javaHome, "bin", exe).toAbsolutePath().toString();
        }
        return "java";
    }
}