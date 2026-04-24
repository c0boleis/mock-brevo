package org.enoria.mockbrevo.brevo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.enoria.mockbrevo.auth.CurrentAccount;
import org.enoria.mockbrevo.brevo.dto.SendSmtpEmailRequest;
import org.enoria.mockbrevo.brevo.dto.SendSmtpEmailResponse;
import org.enoria.mockbrevo.config.MockBrevoProperties;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.SentEmail;
import org.enoria.mockbrevo.domain.SentEmailRepository;
import org.enoria.mockbrevo.smtp.SmtpForwarder;
import org.enoria.mockbrevo.webhook.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v3/smtp/email")
public class TransactionalEmailController {

    private final SentEmailRepository sentEmails;
    private final ObjectMapper objectMapper;
    private final MockBrevoProperties properties;
    private final WebhookService webhookService;
    private final SmtpForwarder smtpForwarder;

    public TransactionalEmailController(
            SentEmailRepository sentEmails,
            ObjectMapper objectMapper,
            MockBrevoProperties properties,
            WebhookService webhookService,
            SmtpForwarder smtpForwarder) {
        this.sentEmails = sentEmails;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.webhookService = webhookService;
        this.smtpForwarder = smtpForwarder;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SendSmtpEmailResponse send(@RequestBody SendSmtpEmailRequest request) {
        Account account = CurrentAccount.require();

        String messageId = "<" + UUID.randomUUID() + "@mock-brevo.local>";

        SentEmail email = new SentEmail();
        email.setAccount(account);
        email.setMessageId(messageId);
        email.setSubject(request.subject() != null ? request.subject() : "(no subject)");
        if (request.sender() != null) {
            email.setSenderEmail(request.sender().email());
            email.setSenderName(request.sender().name());
        }
        if (request.replyTo() != null) {
            email.setReplyTo(request.replyTo().email());
        }
        email.setRecipients(formatRecipients(request.to()));
        email.setTemplateId(request.templateId());
        email.setPayloadJson(serialize(request));
        email.setSentAt(Instant.now());
        sentEmails.save(email);

        smtpForwarder.forward(request, messageId, email.getId());

        if (properties.isAutoFireDelivered()
                && !properties.getDefaultWebhookUrl().isBlank()
                && request.to() != null) {
            for (SendSmtpEmailRequest.EmailAddress to : request.to()) {
                webhookService.fire(
                        properties.getDefaultWebhookUrl(),
                        "delivered",
                        to.email(),
                        null,
                        messageId);
            }
        }

        return new SendSmtpEmailResponse(messageId);
    }

    private String formatRecipients(List<SendSmtpEmailRequest.EmailAddress> to) {
        if (to == null || to.isEmpty()) return "";
        return to.stream()
                .map(a -> a.email() != null ? a.email() : "")
                .collect(Collectors.joining(", "));
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
