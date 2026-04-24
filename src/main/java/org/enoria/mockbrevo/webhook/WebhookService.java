package org.enoria.mockbrevo.webhook;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final RestClient restClient;

    public WebhookService(RestClient restClient) {
        this.restClient = restClient;
    }

    @Async
    public void fire(String url, String event, String email, String reason, String messageId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("email", email);
        payload.put("ts", Instant.now().getEpochSecond());
        payload.put("date", Instant.now().toString());
        if (reason != null) {
            payload.put("reason", reason);
        }
        if (messageId != null) {
            payload.put("message-id", messageId);
        }
        payload.put("tags", new String[0]);

        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Webhook fired: event={} email={} url={}", event, email, url);
        } catch (RestClientException e) {
            log.warn("Webhook POST to {} failed: {}", url, e.getMessage());
        }
    }
}
