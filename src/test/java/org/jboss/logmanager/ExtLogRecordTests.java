package org.jboss.logmanager;

import org.junit.jupiter.api.Test;

/**
 *
 */
public final class ExtLogRecordTests {

    @Test
    public void checkForLongThreadIdRegression() {
        ExtLogRecord rec = new ExtLogRecord(Level.INFO, "Hello world!", ExtLogRecordTests.class.getName());
        // expect this to not blow up on 11 or 17
        rec.setLongThreadID(1234);
    }
}
