package org.enoria.mockbrevo.brevo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateEmailCampaignRequest(
        String name,
        Sender sender,
        String subject,
        Long templateId,
        String replyTo,
        Recipients recipients,
        Map<String, Object> params,
        Boolean inlineImageActivation,
        Boolean mirrorActive,
        String utmCampaign,
        Long folderId
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sender(String name, String email) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recipients(List<Long> listIds, List<Long> exclusionListIds) {}
}
