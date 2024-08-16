package pl.lunasoftware.demo.microservices.loadtest.reader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

public abstract class SqlDataReader implements AutoCloseable {

    static final String DATA_FILE_PARAM = "dataFile";

    protected final RandomAccessFile file;
    protected final long lastLineOffset;

    public SqlDataReader(Path defaultDataFile) {
        try {
            String dataFile = System.getProperty(DATA_FILE_PARAM);
            Path path = dataFile == null
                    ? defaultDataFile
                    : Path.of(dataFile.replaceFirst("^~", System.getProperty("user.home")));
            this.file = new RandomAccessFile(path.toAbsolutePath().toFile(), "r");
            lastLineOffset = lastLineOffset(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        file.close();
    }

    protected String readDataInternal() {
        try {
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
