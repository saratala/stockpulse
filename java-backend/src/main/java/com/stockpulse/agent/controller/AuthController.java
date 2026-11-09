package com.stockpulse.agent.controller;

import com.stockpulse.agent.security.ApiAuthFilter;
import com.stockpulse.agent.security.AuthTokenService;
import com.stockpulse.agent.security.LoginThrottle;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;

/** Single-user login for the web UI. Scripts use the X-API-Key header instead. */
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final byte[] password;
    private final AuthTokenService tokens;
    private final ApiAuthFilter authFilter;
    private final LoginThrottle throttle;

    public AuthController(@Value("${app.security.password:}") String password,
                          AuthTokenService tokens,
                          ApiAuthFilter authFilter,
                          LoginThrottle throttle) {
        this.password = password == null || password.isBlank() ? null : password.getBytes(StandardCharsets.UTF_8);
        this.tokens = tokens;
        this.authFilter = authFilter;
        this.throttle = throttle;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) Map<String, String> body,
                                                     HttpServletRequest request) {
        if (password == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "login_disabled", "message", "APP_PASSWORD is not set"));
        }

        String client = request.getRemoteAddr();
        long waitSeconds = throttle.secondsUntilAllowed(client);
        if (waitSeconds > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(waitSeconds))
                    .body(Map.of("error", "too_many_attempts", "retryAfterSeconds", waitSeconds));
        }

        String provided = body == null ? null : body.get("password");
        if (provided == null || !MessageDigest.isEqual(password, provided.getBytes(StandardCharsets.UTF_8))) {
            throttle.recordFailure(client);
            log.warn("Failed login from {}", client);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_password"));
        }

        throttle.recordSuccess(client);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(tokens.issue(), tokens.lifetime(), request).toString())
                .body(Map.of("authenticated", true));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO, request).toString())
                .body(Map.of("authenticated", false));
    }

    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest request) {
        boolean enabled = authFilter.isEnabled();
        return Map.of(
                "enabled", enabled,
                "authenticated", !enabled || authFilter.isAuthenticated(request));
    }

    private static ResponseCookie sessionCookie(String value, Duration maxAge, HttpServletRequest request) {
        boolean https = request.isSecure()
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        return ResponseCookie.from(ApiAuthFilter.SESSION_COOKIE, value)
                .httpOnly(true)
                .secure(https)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
