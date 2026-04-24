package org.enoria.mockbrevo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sent_email", indexes = {
        @Index(columnList = "account_id"),
        @Index(columnList = "messageId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class SentEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, unique = true, length = 80)
    private String messageId;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(length = 200)
    private String senderEmail;

    @Column(length = 200)
    private String senderName;

    @Column(length = 200)
    private String replyTo;

    @Column(length = 4000)
    private String recipients;

    private Long templateId;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String payloadJson;

    @Column(nullable = false)
    private Instant sentAt;

    private Instant smtpForwardedAt;

    @Column(length = 500)
    private String smtpForwardError;
}
