package com.stockpulse.agent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Stateless session tokens for the single-user login: "v1.&lt;expiryEpochSeconds&gt;.&lt;hmac&gt;".
 * Nothing is stored server side; a token is valid until it expires or the secret changes.
 */
@Slf4j
@Service
public class AuthTokenService {

    private static final String PREFIX = "v1";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secret;
    private final Duration lifetime;
    private final Clock clock;

    @Autowired
    public AuthTokenService(@Value("${app.security.session-secret:}") String configuredSecret,
                            @Value("${app.security.session-hours:168}") long sessionHours) {
        this(configuredSecret, Duration.ofHours(sessionHours), Clock.systemUTC());
    }

    AuthTokenService(String configuredSecret, Duration lifetime, Clock clock) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            log.warn("APP_SESSION_SECRET is not set; using a random secret, so sessions end when the backend restarts");
        } else {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        this.lifetime = lifetime;
        this.clock = clock;
    }

    public Duration lifetime() {
        return lifetime;
    }

    public String issue() {
        long expiry = clock.instant().plus(lifetime).getEpochSecond();
        String payload = PREFIX + "." + expiry;
        return payload + "." + sign(payload);
    }

    public boolean isValid(String token) {
        if (token == null) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            return false;
        }
        long expiry;
        try {
            expiry = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        String expected = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        return clock.instant().getEpochSecond() < expiry;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
