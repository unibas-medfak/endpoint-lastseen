package ch.unibas.medizin.lastseen;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Loads the file on startup, flushes changes periodically, and flushes once more on shutdown. */
public class LastSeenFlusher implements SmartLifecycle {

    private static final Log log = LogFactory.getLog(LastSeenFlusher.class);

    private final EndpointRegistry registry;
    private final JsonFileStore store;
    private final Duration interval;
    private ScheduledExecutorService executor;

    public LastSeenFlusher(EndpointRegistry registry, JsonFileStore store, Duration interval) {
        this.registry = registry;
        this.store = store;
        this.interval = interval;
    }

    @Override
    public synchronized void start() {
        try {
            registry.load(store.read());
        } catch (RuntimeException e) {
            // a corrupt file must not stop the application; it is overwritten by the next flush
            log.warn("Cannot read " + store.file() + ", starting with empty endpoint statistics", e);
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "endpoint-lastseen-flusher");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::flushIfDirty, interval.toMillis(), interval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdown();
            executor = null;
            flushIfDirty();
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }

    /** Runs on the scheduler thread and on stop(); failed writes are retried on the next tick. */
    void flushIfDirty() {
        if (!registry.consumeDirty()) {
            return;
        }
        try {
            store.write(registry.snapshot());
        } catch (IOException | RuntimeException e) {
            registry.markDirty();
            log.warn("Cannot write " + store.file(), e);
        }
    }
}
