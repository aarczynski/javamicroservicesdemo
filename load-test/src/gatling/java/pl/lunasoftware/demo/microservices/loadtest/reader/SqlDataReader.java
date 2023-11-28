package pl.lunasoftware.demo.microservices.loadtest.reader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

public abstract class SqlDataReader {

    protected final Path path;
    protected final long lastLineOffset;

    public SqlDataReader(Path path) {
        this.path = path.toAbsolutePath();
        try (RandomAccessFile file = new RandomAccessFile(this.path.toFile(), "r")) {
            lastLineOffset = lastLineOffset(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected String readDataInternal() {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long randomPos = ThreadLocalRandom.current().nextLong(file.length() - lastLineOffset);
            file.seek(randomPos);
            file.readLine();
            return getData(file.readLine());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract String getData(String line);

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
