package org.enoria.mockbrevo.brevo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateAttributeRequest(
        String type,
        List<String> enumeration,
        Object value,
        Boolean isRecurring,
        List<String> multiCategoryOptions,
        String calculatedValue) {}
