package pl.lunasoftware.demo.microservices.loadtest.reader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

public class DepartmentsSqlDataReader {

    private final Path path;
    private final long lastLineOffset;

    public DepartmentsSqlDataReader() {
        path = Paths.get("data-generator/output/departments.sql").toAbsolutePath();
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            lastLineOffset = lastLineOffset(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String readRandomDepartmentName() {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long randomPos = ThreadLocalRandom.current().nextLong(file.length() - lastLineOffset);
            file.seek(randomPos);
            file.readLine();
            String line = file.readLine().replaceAll("''", "'");
            return line.substring(42, line.length() - 3);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private long lastLineOffset(RandomAccessFile file) throws IOException {
        long pos = file.length();
        int currChar = 0;
        while (currChar != (int)'(') {
            file.seek(pos--);
            currChar = file.read();
        }

        file.seek(++pos);

        return file.readLine().length();
    }
}
