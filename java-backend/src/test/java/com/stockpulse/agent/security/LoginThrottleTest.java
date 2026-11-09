package com.stockpulse.agent.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-09T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void locksOutAfterRepeatedFailuresThenRecovers() {
        MutableClock clock = new MutableClock();
        LoginThrottle throttle = new LoginThrottle(clock);

        for (int i = 1; i < LoginThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure("1.2.3.4");
            assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isZero();
        }
        throttle.recordFailure("1.2.3.4");
        assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isEqualTo(LoginThrottle.LOCKOUT.toSeconds());
        assertThat(throttle.secondsUntilAllowed("5.6.7.8")).isZero();

        clock.now = clock.now.plus(LoginThrottle.LOCKOUT).plus(Duration.ofSeconds(1));
        assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isZero();
    }

    @Test
    void successResetsTheCount() {
        LoginThrottle throttle = new LoginThrottle(new MutableClock());
        for (int i = 1; i < LoginThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure("1.2.3.4");
        }
        throttle.recordSuccess("1.2.3.4");
        throttle.recordFailure("1.2.3.4");
        assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isZero();
    }
}
