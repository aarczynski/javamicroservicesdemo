package pl.lunasoftware.demo.microservices.loadtest.reader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CandidateSqlDataReader implements AutoCloseable {

    private static final Pattern UUID_REGEX = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final RandomAccessFile file;
    private final long lastLineOffset;

    public CandidateSqlDataReader(Path defaultDataFile) {
        try {
            String dataFile = CliParamProvider.CLI_PARAM_PROVIDER.readDataFile();
            Path path = dataFile == null
                    ? defaultDataFile
                    : Path.of(dataFile.replaceFirst("^~", System.getProperty("user.home")));
            this.file = new RandomAccessFile(path.toAbsolutePath().toFile(), "r");
            this.lastLineOffset = lastLineOffset(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String readRandomCandidateId() {
        try {
            long randomPos = ThreadLocalRandom.current().nextLong(file.length() - lastLineOffset - 1);
            file.seek(randomPos);
            file.readLine();
            String line = file.readLine();
            if (line.startsWith("INSERT INTO")) {
                line = file.readLine();
            }
            Matcher m = UUID_REGEX.matcher(line);
            if (m.find()) {
                return m.group();
            } else {
                throw new IllegalStateException(line + " does not contain a UUID");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        file.close();
    }

    private long lastLineOffset(RandomAccessFile file) throws IOException {
        long posBeforeTrailingNewlines = skipTrailingNewlines(file);
        long lastLineStart = findLineStart(file, posBeforeTrailingNewlines);
        file.seek(lastLineStart);
        return file.readLine().length();
    }

    private long skipTrailingNewlines(RandomAccessFile file) throws IOException {
        long pos = file.length() - 1;
        int ch;
        do {
            file.seek(pos--);
            ch = file.read();
        } while (ch == '\n' || ch == '\r');
        return pos;
    }

    private long findLineStart(RandomAccessFile file, long pos) throws IOException {
        int ch;
        do {
            file.seek(pos--);
            ch = file.read();
        } while (ch != '\n');
        return pos + 2;
    }
}
