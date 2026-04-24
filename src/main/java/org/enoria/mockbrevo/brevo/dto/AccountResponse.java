package org.enoria.mockbrevo.brevo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AccountResponse(
        String email,
        String firstName,
        String lastName,
        String companyName,
        @JsonProperty("organization_id") String organizationId,
        @JsonProperty("user_id") Long userId,
        boolean enterprise,
        Address address,
        DateTimePreferences dateTimePreferences,
        List<Plan> plan,
        Relay relay,
        MarketingAutomation marketingAutomation
) {
    public record Plan(
            String type,
            Integer credits,
            String creditsType,
            String startDate,
            String endDate) {}

    public record Address(String street, String city, String zipCode, String country) {}

    public record Relay(boolean enabled, RelayData data) {}

    public record RelayData(String userName, String relay, Integer port) {}

    public record MarketingAutomation(String key, boolean enabled) {}

    public record DateTimePreferences(String timezone, String timeFormat, String dateFormat) {}
}
