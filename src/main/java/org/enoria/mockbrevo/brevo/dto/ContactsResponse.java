package org.enoria.mockbrevo.brevo.dto;

import java.util.List;
import java.util.Map;

public record ContactsResponse(List<ContactItem> contacts, long count) {
    public record ContactItem(
            Long id,
            String email,
            boolean emailBlacklisted,
            boolean smsBlacklisted,
            String createdAt,
            String modifiedAt,
            List<Long> listIds,
            List<Long> listUnsubscribed,
            Map<String, Object> attributes) {}
}
