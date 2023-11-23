package pl.lunasoftware.demo.microservices.datagenerator.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import pl.lunasoftware.demo.microservices.datagenerator.generator.Department;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public class JsonFileWriter {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void writeToFile(Collection<Department> departments, Path path) throws IOException {
        safeCreateFile(path);
        objectMapper.writeValue(path.toFile(), departments);
    }

    private void safeCreateFile(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
        Files.createFile(path);
    }
}
