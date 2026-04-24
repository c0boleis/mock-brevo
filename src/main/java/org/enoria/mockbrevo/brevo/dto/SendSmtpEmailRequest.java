package org.enoria.mockbrevo.brevo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SendSmtpEmailRequest(
        EmailAddress sender,
        List<EmailAddress> to,
        List<EmailAddress> cc,
        List<EmailAddress> bcc,
        EmailAddress replyTo,
        String subject,
        String htmlContent,
        String textContent,
        Long templateId,
        Map<String, Object> params,
        Map<String, String> headers,
        List<Attachment> attachment,
        List<String> tags
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmailAddress(String email, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attachment(String name, String content, String url) {}
}
