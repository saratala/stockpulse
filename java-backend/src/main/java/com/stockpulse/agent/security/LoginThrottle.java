package com.stockpulse.agent.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows password guessing: after {@value #MAX_FAILURES} failed logins from one client
 * address, further attempts are refused until {@link #LOCKOUT} has passed.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant lockedUntil) {}

    private final Map<String, Attempts> byClient = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** 0 when a login attempt is allowed now, otherwise the seconds to wait. */
    public long secondsUntilAllowed(String client) {
        Attempts a = byClient.get(client);
        if (a == null || a.lockedUntil() == null) {
            return 0;
        }
        long wait = Duration.between(clock.instant(), a.lockedUntil()).toSeconds();
        if (wait <= 0) {
            byClient.remove(client);
            return 0;
        }
        return wait;
    }

    public void recordFailure(String client) {
        byClient.compute(client, (k, a) -> {
            int failures = (a == null ? 0 : a.failures()) + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? clock.instant().plus(LOCKOUT) : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    public void recordSuccess(String client) {
        byClient.remove(client);
    }
}
