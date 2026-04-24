package org.enoria.mockbrevo.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String[] PATTERNS = {
            "/v3/**",
            "/mock-webhooks/**"
    };

    private static final int MAX_BODY_BYTES = 16 * 1024;

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final RequestLogStore store;

    public RequestLoggingFilter(RequestLogStore store) {
        this.store = store;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!shouldCapture(path)) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wreq = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wresp = new ContentCachingResponseWrapper(response);

        long start = System.nanoTime();
        Instant at = Instant.now();

        try {
            chain.doFilter(wreq, wresp);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;

            byte[] reqBytes = wreq.getContentAsByteArray();
            byte[] respBytes = wresp.getContentAsByteArray();
            int reqSize = reqBytes.length;
            int respSize = respBytes.length;

            Capture reqCap = capture(reqBytes);
            Capture respCap = capture(respBytes);

            Map<String, String> reqHeaders = extractRequestHeaders(wreq);
            Map<String, String> respHeaders = extractResponseHeaders(wresp);

            RequestLogEntry entry = new RequestLogEntry(
                    store.nextId(),
                    at,
                    request.getMethod(),
                    path,
                    request.getQueryString(),
                    maskKey(request.getHeader("api-key")),
                    wresp.getStatus(),
                    durationMs,
                    reqSize > 0 ? reqSize : null,
                    respSize > 0 ? respSize : null,
                    reqHeaders,
                    reqCap.text,
                    reqCap.truncated,
                    respHeaders,
                    respCap.text,
                    respCap.truncated);
            store.append(entry);

            wresp.copyBodyToResponse();
        }
    }

    private boolean shouldCapture(String path) {
        for (String p : PATTERNS) {
            if (matcher.match(p, path)) return true;
        }
        return false;
    }

    private Map<String, String> extractRequestHeaders(HttpServletRequest r) {
        Map<String, String> out = new LinkedHashMap<>();
        var names = r.getHeaderNames();
        if (names == null) return out;
        for (String n : Collections.list(names)) {
            String key = n.toLowerCase(Locale.ROOT);
            String value = r.getHeader(n);
            if ("api-key".equals(key)) {
                value = maskKey(value);
            } else if ("authorization".equals(key) || "cookie".equals(key)) {
                value = "***";
            }
            out.put(key, value);
        }
        return out;
    }

    private Map<String, String> extractResponseHeaders(ContentCachingResponseWrapper r) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : r.getHeaderNames()) {
            String v = r.getHeader(name);
            if (v != null) out.put(name.toLowerCase(Locale.ROOT), v);
        }
        String ct = r.getContentType();
        if (ct == null) {
            String chk = r.getHeader("Content-Type");
            if (chk != null) ct = chk;
        }
        if (ct != null) {
            out.put("content-type", ct);
        }
        int bodyLen = r.getContentAsByteArray().length;
        if (bodyLen > 0) {
            out.putIfAbsent("content-length", String.valueOf(bodyLen));
        }
        return out;
    }

    private Capture capture(byte[] bytes) {
        if (bytes.length == 0) return new Capture(null, false);
        boolean truncated = bytes.length > MAX_BODY_BYTES;
        int len = Math.min(bytes.length, MAX_BODY_BYTES);
        String text = new String(bytes, 0, len, StandardCharsets.UTF_8);
        return new Capture(text, truncated);
    }

    private static String maskKey(String key) {
        if (key == null || key.isBlank()) return null;
        if (key.length() <= 8) return "***";
        return key.substring(0, 6) + "…" + key.substring(key.length() - 4);
    }

    private record Capture(String text, boolean truncated) {}
}
