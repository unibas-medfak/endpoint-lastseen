package ch.unibas.medizin.lastseen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(classes = LastSeenIntegrationTest.App.class,
        properties = "endpoint-lastseen.file=target/test-lastseen.json")
@AutoConfigureMockMvc
class LastSeenIntegrationTest {

    @SpringBootApplication
    @Import(DemoController.class)
    static class App {
    }

    @RestController
    static class DemoController {
        @GetMapping("/demo/{id}")
        String demo(@PathVariable String id) {
            return id;
        }

        @GetMapping("/unused")
        String unused() {
            return "";
        }
    }

    @BeforeAll
    static void freshFile() throws Exception {
        Files.deleteIfExists(Path.of("target/test-lastseen.json"));
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    EndpointRegistry registry;
    @Autowired
    LastSeenFlusher flusher;

    @Test
    void listsEndpointsTracksLastSeenAndFlushesToFile() throws Exception {
        mvc.perform(get("/demo/1"));
        mvc.perform(get("/demo/2"));

        var demo = find("/demo/{id}");
        assertThat(demo.hits()).isEqualTo(2);
        assertThat(demo.lastSeen()).isNotNull();
        assertThat(demo.lastStatus()).isEqualTo(200);

        var unused = find("/unused");
        assertThat(unused.hits()).isZero();
        assertThat(unused.lastSeen()).isNull();
        assertThat(unused.registered()).isTrue();

        assertThat(registry.snapshot()).noneMatch(r -> r.pattern().equals("/error"));

        flusher.flushIfDirty();
        var json = Files.readString(Path.of("target/test-lastseen.json"));
        assertThat(json).contains("/demo/{id}", "lastSeen");
    }

    private EndpointRecord find(String pattern) {
        return registry.snapshot().stream().filter(r -> r.pattern().equals(pattern)).findFirst().orElseThrow();
    }
}
