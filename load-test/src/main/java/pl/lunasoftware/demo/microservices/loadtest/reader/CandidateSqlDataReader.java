package pl.lunasoftware.demo.microservices.loadtest.reader;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CandidateSqlDataReader extends SqlDataReader {

    private final Pattern uuidRegex = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    public CandidateSqlDataReader(Path defaultDataFile) {
        super(defaultDataFile);
    }

    public String readRandomCandidateId() {
        return readDataInternal();
    }

    @Override
    protected String getData(String line) {
        Matcher m = uuidRegex.matcher(line);
        if (m.find()) {
            return m.group();
        } else {
            throw new IllegalStateException(line + " does not contain a UUID");
        }
    }
}
