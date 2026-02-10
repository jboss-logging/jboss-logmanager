package org.jboss.logmanager.formatters;

import java.util.List;
import java.util.logging.Level;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public final class ColorPatternFormatterTests {

    public ColorPatternFormatterTests() {
    }

    @Test
    public void demoLogColors() {
        // some effort to avoid printing a lot of garbage to the screen
        Assumptions.assumeTrue(System.console() != null);
        runTest();
    }

    private static void runTest() {
        boolean trueColor = ConsoleHandler.isTrueColor();
        System.out.printf("True color: %s%n", trueColor);
        // darken = 0
        for (int darken = 0; darken <= 1; darken++) {
            for (int bg = 0; bg < 2; bg++) {
                System.out.print(switch (bg) {
                    case 0 -> ColorUtil.startBgColor(new StringBuilder(), trueColor, 0, 0, 0);
                    default -> ColorUtil.startBgColor(new StringBuilder(), trueColor, 255, 255, 255);
                });
                ColorPatternFormatter fmt = new ColorPatternFormatter(darken, "%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n");
                System.out.println("Darken = " + darken);
                for (Level l : List.of(
                        Level.OFF,
                        org.jboss.logmanager.Level.FATAL,
                        Level.SEVERE,
                        org.jboss.logmanager.Level.ERROR,
                        Level.WARNING,
                        org.jboss.logmanager.Level.WARN,
                        Level.INFO,
                        org.jboss.logmanager.Level.INFO,
                        Level.CONFIG,
                        org.jboss.logmanager.Level.DEBUG,
                        Level.FINE,
                        org.jboss.logmanager.Level.TRACE,
                        Level.FINER,
                        Level.FINEST,
                        Level.ALL)) {
                    ExtLogRecord record = new ExtLogRecord(
                            l, "Testing level %s %s", ExtLogRecord.FormatStyle.PRINTF,
                            ColorPatternFormatterTests.class.getName());
                    record.setLoggerName("com.acme.logger");
                    record.setParameters(new Object[] { Class.class, "Some text" });
                    System.out.print(fmt.format(record));
                }
                System.out.print(ColorUtil.endBgColor(new StringBuilder()));
            }
        }
    }

    public static void main(String[] args) {
        runTest();
    }
}
