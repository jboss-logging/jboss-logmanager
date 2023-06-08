/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the banner formatting capability.
 */
public class BannerFormatterTests {

    public static final String FALLBACK_OK = "fallback OK!";
    public static final String TEST_BANNER_FILE = "Test banner file!\n";

    @Test
    public void testBanner() throws Exception {
        final PatternFormatter emptyFormatter = new PatternFormatter("");
        final Supplier<String> fallbackSupplier = TextBannerFormatter.createStringSupplier(FALLBACK_OK);
        Assertions.assertEquals("", emptyFormatter.getHead(null));
        Assertions.assertEquals(FALLBACK_OK, fallbackSupplier.get());
        TextBannerFormatter tbf = new TextBannerFormatter(fallbackSupplier, emptyFormatter);
        Assertions.assertEquals(FALLBACK_OK, tbf.getHead(null));
        tbf = new TextBannerFormatter(TextBannerFormatter.createResourceSupplier("non-existent-banner.txt", fallbackSupplier),
                emptyFormatter);
        Assertions.assertEquals(FALLBACK_OK, tbf.getHead(null));
        tbf = new TextBannerFormatter(TextBannerFormatter.createResourceSupplier("test-banner.txt", fallbackSupplier),
                emptyFormatter);
        final InputStream is = BannerFormatterTests.class.getResourceAsStream("/test-banner.txt");
        Assertions.assertNotNull(is);
        try (is) {
            final String s = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertEquals(s, tbf.getHead(null));
        }
        final Path tempFile = Files.createTempFile(Path.of("./target"), "banner-format", null);
        try {
            Files.writeString(tempFile, TEST_BANNER_FILE);
            tbf = new TextBannerFormatter(TextBannerFormatter.createFileSupplier(tempFile, fallbackSupplier), emptyFormatter);
            Assertions.assertEquals(TEST_BANNER_FILE, tbf.getHead(null));
            // and, the URL version...
            tbf = new TextBannerFormatter(TextBannerFormatter.createUrlSupplier(tempFile.toUri().toURL(), fallbackSupplier),
                    emptyFormatter);
            Assertions.assertEquals(TEST_BANNER_FILE, tbf.getHead(null));
        } finally {
            try {
                Files.delete(tempFile);
            } catch (Throwable ignored) {
            }
        }
        // non-existent file
        tbf = new TextBannerFormatter(TextBannerFormatter.createFileSupplier(Path.of("does not exist"), fallbackSupplier),
                emptyFormatter);
        Assertions.assertEquals(FALLBACK_OK, tbf.getHead(null));
    }
}
