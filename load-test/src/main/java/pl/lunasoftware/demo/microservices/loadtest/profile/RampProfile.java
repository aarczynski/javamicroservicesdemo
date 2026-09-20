package pl.lunasoftware.demo.microservices.loadtest.profile;

import java.time.Duration;

public final class RampProfile {

    private static final Duration MAX_RAMP_DURATION = Duration.ofMinutes(1);

    private RampProfile() {
    }

    public static Duration rampDuration(Duration stepDuration) {
        return stepDuration.compareTo(MAX_RAMP_DURATION) < 0 ? stepDuration : MAX_RAMP_DURATION;
    }

    public static Duration holdDuration(Duration stepDuration) {
        return stepDuration.minus(rampDuration(stepDuration));
    }
}
