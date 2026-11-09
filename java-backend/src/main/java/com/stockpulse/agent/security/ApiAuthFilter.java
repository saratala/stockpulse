package com.stockpulse.agent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Every API request needs either the session cookie set by POST /auth/login (the web UI)
 * or the X-API-Key header (scripts). Only health checks and the auth endpoints are public.
 *
 * Fails closed: with auth enabled and neither APP_PASSWORD nor APP_API_KEY set, the
 * application refuses to start rather than serving an open API.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_COOKIE = "sp_session";
    public static final String API_KEY_HEADER = "X-API-Key";

    /** Paths relative to the servlet context (/api/v1). */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/health",
            "/actuator/health",
            "/auth/login",
            "/auth/logout",
            "/auth/status"
    );

    private final boolean enabled;
    private final byte[] apiKey;
    private final AuthTokenService tokens;

    public ApiAuthFilter(@Value("${app.security.enabled:true}") boolean enabled,
                         @Value("${app.security.password:}") String password,
                         @Value("${app.security.api-key:}") String apiKey,
                         AuthTokenService tokens) {
        this.enabled = enabled;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.getBytes(StandardCharsets.UTF_8);
        this.tokens = tokens;

        if (!enabled) {
            log.warn("API authentication is DISABLED (APP_AUTH_ENABLED=false); every endpoint is open");
        } else if ((password == null || password.isBlank()) && this.apiKey == null) {
            throw new IllegalStateException(
                    "API authentication is enabled but neither APP_PASSWORD nor APP_API_KEY is set. "
                    + "Set them in .env, or set APP_AUTH_ENABLED=false for a private local-only setup.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** True when the request carries a valid session cookie or API key. */
    public boolean isAuthenticated(HttpServletRequest request) {
        return hasValidApiKey(request) || tokens.isValid(sessionToken(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || PUBLIC_PATHS.contains(pathWithinContext(request))
                || isAuthenticated(request)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Sign in or send X-API-Key\"}");
    }

    private boolean hasValidApiKey(HttpServletRequest request) {
        String provided = request.getHeader(API_KEY_HEADER);
        return apiKey != null && provided != null
                && MessageDigest.isEqual(apiKey, provided.getBytes(StandardCharsets.UTF_8));
    }

    private static String sessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Raw (undecoded, unnormalized) request path below the context path. Public paths
     * are matched exactly against it, so any trick that makes Tomcat route somewhere
     * else ("/health/../trading/run", "%2e%2e", ";params") simply fails to match and
     * the request needs credentials. Path parameters are rejected outright: Tomcat
     * strips them, so "/health;/../trading/run" would otherwise route to /trading/run.
     */
    static String pathWithinContext(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.indexOf(';') >= 0) {
            return "";
        }
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
