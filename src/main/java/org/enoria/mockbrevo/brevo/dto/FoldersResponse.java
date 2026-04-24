package org.enoria.mockbrevo.brevo.dto;

import java.util.List;

public record FoldersResponse(List<FolderItem> folders, long count) {
    public record FolderItem(
            Long id,
            String name,
            long totalSubscribers,
            long totalBlacklisted,
            long uniqueSubscribers) {}
}
