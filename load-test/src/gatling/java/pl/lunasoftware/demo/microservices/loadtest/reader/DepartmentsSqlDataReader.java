package pl.lunasoftware.demo.microservices.loadtest.reader;

import java.nio.file.Paths;

public class DepartmentsSqlDataReader extends SqlDataReader {

    public DepartmentsSqlDataReader() {
        super(Paths.get("data-generator/output/departments.sql"));
    }

    public String readRandomDepartmentName() {
        return readDataInternal();
    }

    @Override
    protected String getData(String line) {
        return line.replaceAll("''", "'").substring(42, line.length() - 3);
    }
}
