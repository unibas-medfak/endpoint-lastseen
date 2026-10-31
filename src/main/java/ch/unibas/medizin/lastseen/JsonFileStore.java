package ch.unibas.medizin.lastseen;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Reads and writes the endpoint table as a JSON array. Writes are atomic (temp file + move). */
public class JsonFileStore {

    private final Path file;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public JsonFileStore(Path file) {
        this.file = file.toAbsolutePath();
    }

    public List<EndpointRecord> read() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        return mapper.readValue(file.toFile(), new TypeReference<>() {
        });
    }

    public void write(List<EndpointRecord> records) throws IOException {
        var dir = file.getParent();
        Files.createDirectories(dir);
        var tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            mapper.writeValue(tmp.toFile(), records);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public Path file() {
        return file;
    }
}
