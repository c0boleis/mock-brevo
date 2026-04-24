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
@Table(name = "smtp_template", indexes = @Index(columnList = "account_id"))
@Getter
@Setter
@NoArgsConstructor
public class SmtpTemplate {

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

    @Column(length = 200)
    private String toField;

    @Column(length = 200)
    private String tag;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String htmlContent;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant modifiedAt;
}
