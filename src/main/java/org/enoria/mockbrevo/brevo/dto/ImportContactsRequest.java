package org.enoria.mockbrevo.brevo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportContactsRequest(
        String fileBody,
        String fileUrl,
        List<Long> listIds,
        Boolean updateExistingContacts,
        Boolean emailBlacklist,
        Boolean smsBlacklist,
        Boolean emptyContactsAttributes,
        NewList newList
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NewList(String listName, Long folderId) {}
}
