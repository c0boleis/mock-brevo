package org.enoria.mockbrevo.brevo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateSmtpTemplateRequest(
        String templateName,
        String subject,
        String htmlContent,
        String htmlUrl,
        Sender sender,
        String replyTo,
        String toField,
        String tag,
        Boolean isActive
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sender(String name, String email) {}
}
