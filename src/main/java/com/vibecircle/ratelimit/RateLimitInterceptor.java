package com.vibecircle.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs after authentication, so it knows who is asking. Public auth endpoints are limited per client IP
 * (behind a proxy set server.forward-headers-strategy=native so the real IP is used); everything else per user.
 * A limited request gets 429 with a Retry-After header.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration QUARTER_HOUR = Duration.ofMinutes(15);
    private static final Duration HOUR = Duration.ofHours(1);

    private static final Set<String> AUTH_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/verify-email",
            "/api/auth/forgot-password", "/api/auth/reset-password");

    private final RateLimiter limiter;
    private final RateLimitProperties props;

    public RateLimitInterceptor(RateLimiter limiter, RateLimitProperties props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!props.enabled() || "OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();
        String ip = request.getRemoteAddr();
        String user = currentUser();
        String subject = user != null ? "u:" + user : "ip:" + ip;
        boolean write = !"GET".equals(method) && !"HEAD".equals(method);

        if (AUTH_PATHS.contains(path)) {
            if (!allow(response, path, "auth:" + ip, props.authPerMinute(), MINUTE)) return false;
            if (path.endsWith("/register")
                    && !allow(response, path, "signup:" + ip, props.signupPerHour(), HOUR)) return false;
            if ((path.endsWith("/forgot-password") || path.endsWith("/reset-password"))
                    && !allow(response, path, "reset:" + ip, props.resetPerHour(), HOUR)) return false;
            return true;
        }
        if (write && isMultipart(request)
                && !allow(response, path, "upload:" + subject, props.uploadsPerQuarterHour(), QUARTER_HOUR)) {
            return false;
        }
        if (write && "POST".equals(method) && "/api/friends/requests".equals(path)
                && !allow(response, path, "friendreq:" + subject, props.friendRequestsPerHour(), HOUR)) {
            return false;
        }
        return write
                ? allow(response, path, "write:" + subject, props.writesPerMinute(), MINUTE)
                : allow(response, path, "read:" + subject, props.readsPerMinute(), MINUTE);
    }

    private boolean allow(HttpServletResponse response, String path, String key, int limit, Duration window) throws IOException {
        RateLimiter.Decision decision = limiter.tryAcquire(key, limit, window);
        if (decision.allowed()) {
            return true;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Rate limit exceeded. Try again in " + decision.retryAfterSeconds() + " seconds.\","
                + "\"instance\":\"" + path.replace("\\", "").replace("\"", "") + "\"}");
        return false;
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.toLowerCase().startsWith("multipart/");
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken token ? token.getName() : null;
    }
}
