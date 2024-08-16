package pl.lunasoftware.demo.microservices.loadtest.reader;

import java.nio.file.Path;

public class DepartmentsSqlDataReader extends SqlDataReader {

    public DepartmentsSqlDataReader(Path defaultDataFile) {
        super(defaultDataFile);
    }

    public String readRandomDepartmentName() {
        return readDataInternal();
    }

    @Override
    protected String getData(String line) {
        return line.replaceAll("''", "'").substring(42, line.length() - 3);
    }
}
