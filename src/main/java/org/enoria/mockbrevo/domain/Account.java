package org.enoria.mockbrevo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String apiKey;

    @Column(length = 36)
    private String organizationId;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 200)
    private String companyName;

    @Column(nullable = false)
    private Integer credits;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    public static Account provision(String apiKey) {
        Account a = new Account();
        Instant now = Instant.now();
        String suffix = suffix(apiKey);
        a.apiKey = apiKey;
        a.organizationId = deriveOrganizationId(apiKey);
        a.email = "mock-" + suffix + "@mock-brevo.local";
        a.firstName = "Mock";
        a.lastName = "Account-" + suffix;
        a.companyName = "Mock Brevo Account " + suffix;
        a.credits = 10_000;
        a.createdAt = now;
        a.lastSeenAt = now;
        return a;
    }

    public static String deriveOrganizationId(String apiKey) {
        return UUID.nameUUIDFromBytes(("mock-brevo-org:" + apiKey).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static String suffix(String apiKey) {
        int h = apiKey.hashCode() & 0x7FFFFFFF;
        return String.format("%08x", h);
    }
}
