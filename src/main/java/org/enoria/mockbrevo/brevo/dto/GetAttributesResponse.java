package org.enoria.mockbrevo.brevo.dto;

import java.util.List;

public record GetAttributesResponse(List<AttributeItem> attributes) {
    public record AttributeItem(
            String name,
            String category,
            String type,
            List<String> enumeration,
            String calculatedValue,
            Object value,
            List<String> multiCategoryOptions) {}
}
