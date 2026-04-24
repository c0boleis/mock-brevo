package org.enoria.mockbrevo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "email_campaign", indexes = @Index(columnList = "account_id"))
@Getter
@Setter
@NoArgsConstructor
public class EmailCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String subject;

    @Column(length = 200)
    private String senderName;

    @Column(length = 200)
    private String senderEmail;

    @Column(length = 200)
    private String replyTo;

    private Long templateId;

    @Column(length = 4000)
    private String recipientListIdsCsv;

    @Column(length = 200)
    private String utmCampaign;

    @Column(length = 4000)
    private String paramsJson;

    @Column(nullable = false, length = 30)
    private String status = "draft";

    private Instant sentDate;

    @Column(nullable = false)
    private Instant createdAt;

    private Integer deliveredCount;
}
