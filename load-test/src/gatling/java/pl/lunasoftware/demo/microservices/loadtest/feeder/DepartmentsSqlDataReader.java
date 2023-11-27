package pl.lunasoftware.demo.microservices.loadtest.feeder;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

public class DepartmentsSqlDataReader {

    private final Path path;
    private final int firstLineOffset;
    private final long lastLineOffset;

    public DepartmentsSqlDataReader() {
        path = Paths.get("data-generator/output/departments.sql").toAbsolutePath();
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            firstLineOffset = firstLineOffset(file);
            lastLineOffset = lastLineOffset(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String readRandomDepartmentName() {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long randomPos = ThreadLocalRandom.current().nextLong(firstLineOffset, file.length() - lastLineOffset);
            file.seek(randomPos);
            file.readLine();
            String line = file.readLine().replaceAll("''", "'");
            return line.substring(42, line.length() - 3);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private long lastLineOffset(RandomAccessFile file) throws IOException {
        String l;
        long lastLineOffset = 0;
        while ((l = file.readLine()) != null) {
            lastLineOffset = l.length();
        }
        return lastLineOffset;
    }

    private int firstLineOffset(RandomAccessFile file) throws IOException {
        file.seek(0);
        return file.readLine().length();
    }
}
