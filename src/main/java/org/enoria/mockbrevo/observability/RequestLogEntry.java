package org.enoria.mockbrevo.observability;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record RequestLogEntry(
        long id,
        Instant at,
        String method,
        String path,
        String query,
        String apiKeyPreview,
        Integer status,
        long durationMs,
        Integer requestSize,
        Integer responseSize,
        Map<String, String> requestHeaders,
        String requestBody,
        boolean requestTruncated,
        Map<String, String> responseHeaders,
        String responseBody,
        boolean responseTruncated
) {
    public RequestLogEntry withoutBodies() {
        return new RequestLogEntry(
                id, at, method, path, query, apiKeyPreview, status, durationMs,
                requestSize, responseSize,
                null, null, false, null, null, false);
    }
}
