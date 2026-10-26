package org.enoria.mockbrevo.brevo.dto;

import java.util.List;
import java.util.Map;

public record GetContactInfoResponse(
        Long id,
        String email,
        boolean emailBlacklisted,
        boolean smsBlacklisted,
        String createdAt,
        String modifiedAt,
        List<Long> listIds,
        Map<String, Object> attributes) {}
