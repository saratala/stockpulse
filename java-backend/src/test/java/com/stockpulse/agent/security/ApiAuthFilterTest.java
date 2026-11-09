package com.stockpulse.agent.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiAuthFilterTest {

    private static final String KEY = "test-api-key";
    private final AuthTokenService tokens = new AuthTokenService("secret", Duration.ofHours(1), Clock.systemUTC());
    private final ApiAuthFilter filter = new ApiAuthFilter(true, "pw", KEY, tokens);

    private static MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath("/api/v1");
        return request;
    }

    /** Runs the filter; returns true when the request reached the controller. */
    private boolean passes(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        if (chain.getRequest() == null) {
            assertThat(response.getStatus()).isEqualTo(401);
            return false;
        }
        return true;
    }

    @Test
    void rejectsRequestsWithoutCredentials() throws Exception {
        assertThat(passes(get("/api/v1/stocks"))).isFalse();
        assertThat(passes(get("/api/v1/trading/status"))).isFalse();
        assertThat(passes(get("/api/v1/actuator/metrics"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/health", "/api/v1/actuator/health", "/api/v1/auth/login",
            "/api/v1/auth/status", "/api/v1/health/"})
    void leavesHealthAndAuthEndpointsPublic(String uri) throws Exception {
        assertThat(passes(get(uri))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/health;/../trading/run",
            "/api/v1/health;x",
            "/api/v1/health/../trading/run",
            "/api/v1/health/%2e%2e/trading/run",
            "/api/v1//health"})
    void pathTricksDoNotReachPublicPaths(String uri) throws Exception {
        assertThat(passes(get(uri))).isFalse();
    }

    @Test
    void acceptsTheApiKeyHeader() throws Exception {
        MockHttpServletRequest request = get("/api/v1/stocks");
        request.addHeader("X-API-Key", KEY);
        assertThat(passes(request)).isTrue();
    }

    @Test
    void rejectsAWrongApiKey() throws Exception {
        MockHttpServletRequest request = get("/api/v1/stocks");
        request.addHeader("X-API-Key", KEY + "x");
        assertThat(passes(request)).isFalse();
    }

    @Test
    void acceptsAValidSessionCookieAndRejectsATamperedOne() throws Exception {
        String token = tokens.issue();

        MockHttpServletRequest valid = get("/api/v1/stocks");
        valid.setCookies(new Cookie(ApiAuthFilter.SESSION_COOKIE, token));
        assertThat(passes(valid)).isTrue();

        String[] parts = token.split("\\.");
        String extended = parts[0] + "." + (Long.parseLong(parts[1]) + 86_400) + "." + parts[2];
        MockHttpServletRequest tampered = get("/api/v1/stocks");
        tampered.setCookies(new Cookie(ApiAuthFilter.SESSION_COOKIE, extended));
        assertThat(passes(tampered)).isFalse();
    }

    @Test
    void sessionTokensExpire() {
        Instant now = Instant.parse("2026-11-09T12:00:00Z");
        AuthTokenService issuer = new AuthTokenService("secret", Duration.ofHours(1), Clock.fixed(now, ZoneOffset.UTC));
        AuthTokenService later = new AuthTokenService("secret", Duration.ofHours(1),
                Clock.fixed(now.plus(Duration.ofMinutes(61)), ZoneOffset.UTC));

        String token = issuer.issue();
        assertThat(issuer.isValid(token)).isTrue();
        assertThat(later.isValid(token)).isFalse();
    }

    @Test
    void tokensFromAnotherSecretAreRejected() {
        AuthTokenService other = new AuthTokenService("different", Duration.ofHours(1), Clock.systemUTC());
        assertThat(tokens.isValid(other.issue())).isFalse();
        assertThat(tokens.isValid("garbage")).isFalse();
        assertThat(tokens.isValid(null)).isFalse();
    }

    @Test
    void refusesToStartWithAuthEnabledAndNoCredentials() {
        assertThatThrownBy(() -> new ApiAuthFilter(true, "", "", tokens))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_PASSWORD");
    }

    @Test
    void passesEverythingWhenExplicitlyDisabled() throws Exception {
        ApiAuthFilter disabled = new ApiAuthFilter(false, "", "", tokens);
        MockFilterChain chain = new MockFilterChain();
        disabled.doFilter(get("/api/v1/trading/status"), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }
}
