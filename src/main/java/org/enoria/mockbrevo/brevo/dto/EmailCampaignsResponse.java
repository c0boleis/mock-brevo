package org.enoria.mockbrevo.brevo.dto;

import java.util.List;
import java.util.Map;

public record EmailCampaignsResponse(List<CampaignItem> campaigns, long count) {

    public record CampaignItem(
            Long id,
            String name,
            String subject,
            String type,
            String status,
            String scheduledAt,
            Boolean abTesting,
            String sentDate,
            String createdAt,
            String modifiedAt,
            Boolean inlineImageActivation,
            Boolean mirrorActive,
            Boolean testSent,
            String tag,
            String shareLink,
            String utmCampaignValue,
            String htmlContent,
            String header,
            String footer,
            SenderRef sender,
            String replyTo,
            Recipients recipients,
            Statistics statistics) {}

    public record SenderRef(Long id, String name, String email) {}

    public record Recipients(List<Long> lists, List<Long> exclusionLists) {}

    public record Statistics(
            List<CampaignStats> campaignStats,
            CampaignStats globalStats,
            LinksStats linksStats,
            long mirrorClick,
            long remaining,
            Map<String, DeviceBrowserStats> statsByBrowser,
            StatsByDevice statsByDevice,
            Map<String, CampaignStats> statsByDomain) {}

    public record CampaignStats(
            long clickers,
            long complaints,
            long delivered,
            long hardBounces,
            long sent,
            long softBounces,
            long trackableViews,
            long uniqueClicks,
            long uniqueViews,
            long unsubscriptions,
            long viewed,
            Long listId) {}

    public record DeviceBrowserStats(
            long clickers,
            long uniqueClicks,
            long uniqueViews,
            long viewed) {}

    public record StatsByDevice(
            Map<String, DeviceBrowserStats> desktop,
            Map<String, DeviceBrowserStats> mobile,
            Map<String, DeviceBrowserStats> tablet,
            Map<String, DeviceBrowserStats> unknown) {}

    public record LinksStats() {}
}
