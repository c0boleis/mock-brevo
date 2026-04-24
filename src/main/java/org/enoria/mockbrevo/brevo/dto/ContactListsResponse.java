package org.enoria.mockbrevo.brevo.dto;

import java.util.List;

public record ContactListsResponse(List<ListItem> lists, long count) {
    public record ListItem(
            Long id,
            String name,
            long totalBlacklisted,
            long totalSubscribers,
            long uniqueSubscribers,
            Long folderId) {}
}
