package ch.unibas.medizin.lastseen;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/** In-memory endpoint table. {@link #touch} is lock-free and safe on the request path. */
public class EndpointRegistry {

    private static final class Entry {
        final String method;
        final String pattern;
        volatile String handler;
        volatile Instant lastSeen;
        volatile Integer lastStatus;
        volatile boolean registered;
        final LongAdder hits = new LongAdder();

        Entry(String method, String pattern) {
            this.method = method;
            this.pattern = pattern;
        }

        EndpointRecord toRecord() {
            return new EndpointRecord(method, pattern, handler, lastSeen, hits.sum(), lastStatus, registered);
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final Clock clock;

    public EndpointRegistry(Clock clock) {
        this.clock = clock;
    }

    /** Marks an endpoint as present in the code. Keeps any previously loaded statistics. */
    public void register(String method, String pattern, String handler) {
        var e = entries.computeIfAbsent(key(method, pattern), k -> new Entry(method, pattern));
        e.handler = handler;
        e.registered = true;
        dirty.set(true);
    }

    /** Records that a request reached the endpoint. */
    public void touch(String method, String pattern, int status) {
        var e = entries.get(key(method, pattern));
        if (e == null) {
            e = entries.computeIfAbsent(key(method, pattern), k -> new Entry(method, pattern));
            e.registered = true;
        }
        e.lastSeen = clock.instant();
        e.lastStatus = status;
        e.hits.increment();
        dirty.set(true);
    }

    /** Merges statistics loaded from the file. Entries not registered afterwards are reported as removed. */
    public void load(List<EndpointRecord> records) {
        for (var r : records) {
            var e = entries.computeIfAbsent(key(r.method(), r.pattern()), k -> new Entry(r.method(), r.pattern()));
            if (e.handler == null) {
                e.handler = r.handler();
            }
            if (r.lastSeen() != null && (e.lastSeen == null || r.lastSeen().isAfter(e.lastSeen))) {
                e.lastSeen = r.lastSeen();
                e.lastStatus = r.lastStatus();
            }
            e.hits.add(r.hits());
        }
    }

    public List<EndpointRecord> snapshot() {
        return entries.values().stream()
                .map(Entry::toRecord)
                .sorted(Comparator.comparing(EndpointRecord::pattern).thenComparing(EndpointRecord::method))
                .toList();
    }

    /** Returns true once per modification batch, so the caller knows whether a flush is needed. */
    boolean consumeDirty() {
        return dirty.getAndSet(false);
    }

    void markDirty() {
        dirty.set(true);
    }

    private static String key(String method, String pattern) {
        return method + ' ' + pattern;
    }
}
