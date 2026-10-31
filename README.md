# endpoint-lastseen

Spring Boot starter that lists all Spring MVC endpoints of an application and records when each one was last accessed. No changes to the application code are needed: add the dependency and the starter wires itself in through auto-configuration.

The result is a JSON file, flushed periodically, which shows endpoints that have never been called (candidates for dead code) and how often and when the others are used.

## Requirements

- Spring Boot 4.x (built against 4.2.0-RC1), Java 21
- Servlet-based Spring MVC. WebFlux server endpoints are not tracked.

## Usage

```xml
<dependency>
    <groupId>ch.unibas.medizin</groupId>
    <artifactId>endpoint-lastseen-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Start the application. After the first flush (30 seconds by default) `endpoint-lastseen.json` appears in the working directory:

```json
[ {
  "method" : "GET",
  "pattern" : "/person/{id}",
  "handler" : "com.example.PersonController#get",
  "lastSeen" : "2026-10-30T17:54:23.691341Z",
  "hits" : 42,
  "lastStatus" : 200,
  "registered" : true
}, {
  "method" : "GET",
  "pattern" : "/legacy/report",
  "handler" : "com.example.LegacyController#report",
  "lastSeen" : null,
  "hits" : 0,
  "lastStatus" : null,
  "registered" : true
} ]
```

| Field | Meaning |
|---|---|
| `method` | HTTP method, or `ANY` if the mapping does not restrict it |
| `pattern` | Declared path pattern, so `/person/1` and `/person/2` count as `/person/{id}` |
| `handler` | `Class#method` of the controller method |
| `lastSeen` | Last time a request reached the handler. `null` if never seen |
| `hits` | Number of requests that reached the handler |
| `lastStatus` | HTTP status of the last request |
| `registered` | `false` if the endpoint is in the file but no longer in the code |

## Configuration

All properties are optional.

```yaml
endpoint-lastseen:
  enabled: true                     # set to false to switch the starter off
  file: endpoint-lastseen.json      # where the statistics are stored
  flush-interval: 30s               # how often changes are written
  exclude-patterns:                 # path patterns that are neither listed nor tracked
    - /error
    - /actuator/**
```

## How it works

- **Endpoint list:** on `ApplicationReadyEvent`, every `RequestMappingHandlerMapping` is read, giving one entry per HTTP method and path pattern.
- **Tracking:** a `HandlerInterceptor` records the request in `afterCompletion`. The update is lock-free (`ConcurrentHashMap` plus `LongAdder`), so it adds no database or file I/O to the request path.
- **Persistence:** the file is read on startup and merged into the in-memory table, so statistics survive restarts. A background thread writes it when something changed, and once more on shutdown. Writes are atomic (temp file, then move).
- **Failures:** a corrupt or unreadable file logs a warning and the application starts with empty statistics. A failed write is retried on the next flush.

## Limitations

- **Requests rejected before the controller** (401/403 from Spring Security, 404, filter rejections) are not counted. "Last seen" means "last reached a controller method".
- **One instance per file.** With several instances sharing a file, the last writer wins and hits are lost. Give each instance its own `endpoint-lastseen.file` and merge the files afterwards.
- **A crash loses up to one flush interval** of data.
- **Only HTTP controller endpoints are covered.** Code reached through scheduled jobs, message queues or imports does not show up, so a zero-hit endpoint does not prove that the service code behind it is unused.
- **Entries stay in the file** even after they match a new exclude pattern. Delete the file after changing `exclude-patterns`.

## Finding dead code

1. Collect for at least one full business cycle (for seasonal applications, a full year), from all instances.
2. Take the endpoints with `hits: 0` and `registered: true` as candidates.
3. Check each candidate against its callers (frontend, other services, external consumers).
4. Deprecate first, for example by returning `410 Gone` for a release, then delete.
5. Use static analysis to find the service, repository and DTO code that became orphaned.

## Build

```
mvn clean test
```

The test starts a small application with MockMvc and checks the endpoint list, the counters, the exclude patterns and the JSON file.
