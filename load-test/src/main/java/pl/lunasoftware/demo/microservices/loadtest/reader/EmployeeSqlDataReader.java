package pl.lunasoftware.demo.microservices.loadtest.reader;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmployeeSqlDataReader extends SqlDataReader {

    private final Pattern emailRegex = Pattern.compile("([a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+)");

    public EmployeeSqlDataReader(Path defaultDataFile) {
        super(defaultDataFile);
    }

    public String readRandomEmployeeEmail() {
        return readDataInternal();
    }

    @Override
    protected String getData(String line) {
        Matcher m = emailRegex.matcher(line);
        if (m.find()) {
            return m.group();
        } else {
            throw new IllegalStateException(line + " does not contain email");
        }
    }
}
