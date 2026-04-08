package pl.lunasoftware.demo.microservices.loadtest.reader;

public enum CliParamProvider {
    CLI_PARAM_PROVIDER;

    static final String DATA_FILE_PARAM = "candidatesDataFile";
    static final String HOST_PARAM = "targetHost";

    public String readDataFile() {
        return System.getProperty(DATA_FILE_PARAM);
    }

    public String readHost() {
        return System.getProperty(HOST_PARAM);
    }

}
