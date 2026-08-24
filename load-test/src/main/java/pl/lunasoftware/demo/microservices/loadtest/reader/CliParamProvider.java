package pl.lunasoftware.demo.microservices.loadtest.reader;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CliParamProvider {

    static final String DATA_FILE_PARAM = "candidatesDataFile";
    static final String HOST_PARAM = "targetHost";
    static final String MAX_RPS_PARAM = "maxRps";
    static final String STEP_DURATION_PARAM = "stepDuration";
    static final String RAMPS_PARAM = "ramps";

    private static final int DEFAULT_MAX_RPS = 500;
    private static final Duration DEFAULT_STEP_DURATION = Duration.ofSeconds(60);
    private static final int DEFAULT_RAMPS = 5;

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(s|m|h)$");

    private CliParamProvider() {
    }

    public static String readDataFile() {
        return System.getProperty(DATA_FILE_PARAM);
    }

    public static String readHost() {
        return System.getProperty(HOST_PARAM);
    }

    public static int readMaxRps() {
        return readInt(MAX_RPS_PARAM, DEFAULT_MAX_RPS);
    }

    public static Duration readStepDuration() {
        String value = System.getProperty(STEP_DURATION_PARAM);
        if (value == null) {
            return DEFAULT_STEP_DURATION;
        }
        return parseDuration(value);
    }

    public static int readRamps() {
        return readInt(RAMPS_PARAM, DEFAULT_RAMPS);
    }

    private static int readInt(String param, int defaultValue) {
        String value = System.getProperty(param);
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static Duration parseDuration(String value) {
        Matcher matcher = DURATION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration format: '" + value + "'. Expected format: <number><unit> where unit is s, m, or h (e.g. 30s, 5m, 1h)");
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> throw new IllegalStateException("Unexpected unit: " + matcher.group(2));
        };
    }
}
