package org.enoria.mockbrevo.brevo.dto;

import java.util.List;

public record SmtpTemplatesResponse(List<TemplateItem> templates, long count) {
    public record TemplateItem(
            Long id,
            String name,
            String subject,
            boolean isActive,
            boolean testSent,
            SenderRef sender,
            String replyTo,
            String toField,
            String tag,
            String htmlContent,
            String createdAt,
            String modifiedAt
    ) {}

    public record SenderRef(String name, String email) {}
}
