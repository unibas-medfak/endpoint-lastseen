package ch.unibas.medizin.lastseen;

import java.time.Instant;

/**
 * Immutable snapshot of one endpoint, as exposed to callers and written to the JSON file.
 *
 * @param method     HTTP method, or {@code ANY} if the mapping does not restrict it
 * @param pattern    path pattern as declared (e.g. {@code /person/{id}})
 * @param handler    {@code Class#method} of the handler, null if unknown
 * @param lastSeen   last time a request reached the handler, null if never seen
 * @param hits       number of requests that reached the handler
 * @param lastStatus HTTP status of the last request, null if never seen
 * @param registered false if the endpoint exists only in the file, i.e. it was removed from the code
 */
public record EndpointRecord(String method, String pattern, String handler, Instant lastSeen,
                             long hits, Integer lastStatus, boolean registered) {
}
