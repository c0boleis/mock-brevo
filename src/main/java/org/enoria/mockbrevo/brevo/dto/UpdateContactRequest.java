package org.enoria.mockbrevo.brevo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateContactRequest(
        Map<String, Object> attributes,
        Boolean emailBlacklisted,
        Boolean smsBlacklisted,
        List<Long> listIds,
        List<Long> unlinkListIds) {}
