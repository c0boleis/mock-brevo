package org.enoria.mockbrevo.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import org.enoria.mockbrevo.webhook.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-webhooks")
public class MockWebhooksController {

    private final WebhookService webhookService;

    public MockWebhooksController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/fire")
    public ResponseEntity<Map<String, Object>> fire(@RequestBody FireRequest body) {
        if (body == null || body.url == null || body.url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }
        if (body.event == null || body.event.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "event is required"));
        }
        if (body.email == null || body.email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        webhookService.fire(body.url, body.event, body.email, body.reason, body.messageId);
        return ResponseEntity.accepted().body(Map.of("status", "fired", "event", body.event, "email", body.email));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FireRequest {
        public String url;
        public String event;
        public String email;
        public String reason;
        public String messageId;
    }
}
