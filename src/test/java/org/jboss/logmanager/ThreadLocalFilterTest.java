package org.jboss.logmanager;

import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;

public class ThreadLocalFilterTest {

    @Before
    public void setSystemProperty() {
        System.setProperty(LogManager.PER_THREAD_LOG_FILTER_KEY, "true");
    }

    @After
    public void resetSystemProperty() {
        System.setProperty(LogManager.PER_THREAD_LOG_FILTER_KEY, "false");
    }

    @Test
    public void setThreadLocalLogLevel() throws Exception {

        final Logger log = Logger.getLogger("THREAD_LOCAL");
        StringListHandler logHandler = new StringListHandler();

        Formatter f = new PatternFormatter("%t %m");
        logHandler.setFormatter(f);
        log.setHandlers(new Handler[] {logHandler});

        final ExecutorService threadPool = Executors.newFixedThreadPool(2);

        threadPool.submit(taskWithLogLevel(Level.DEBUG, "_DEBUG_"));
        threadPool.submit(taskWithLogLevel(Level.INFO, "_INFO_"));

        threadPool.shutdown();

        threadPool.awaitTermination(10L, TimeUnit.SECONDS);

        logHandler.flush();
        final List<String> messages = logHandler.getMessages();
        assertEquals(4, messages.size());
        int countDebug = 0;
        for (String msg : messages) {
            if (msg.startsWith("_DEBUG_")) {
                countDebug++;
            }
        }
        assertEquals(3, countDebug);
        int countInfo = 0;
        for (String msg : messages) {
            if (msg.startsWith("_INFO_")) {
                countInfo++;
            }
        }
        assertEquals(1, countInfo);
    }

    private FutureTask<Void> taskWithLogLevel(final Level logLevel, final String id) {
        return new FutureTask<>(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                LogManager.setThreadLocalLogLevel(new ThreadLocalLevelFilter(logLevel));
                Logger log = Logger.getLogger("THREAD_LOCAL");
                log.info(id + " - INFO");
                log.fine(id + " - DEBUG");
                if (log.isLoggable(Level.DEBUG)) {
                    log.log(Level.DEBUG, id + " - DEBUG");
                }
                return null;
            }
        });
    }

    private static final class ThreadLocalLevelFilter implements Filter {

        private final Level level;

        public ThreadLocalLevelFilter(Level level) {
            this.level = level;
        }

        @Override
        public boolean isLoggable(LogRecord record) {
            return record.getLevel().intValue() >= this.level.intValue();
        }
    }
}
