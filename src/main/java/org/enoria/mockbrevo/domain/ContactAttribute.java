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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "contact_attribute_definition",
        indexes = @Index(columnList = "account_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "category", "name"})
)
@Getter
@Setter
@NoArgsConstructor
public class ContactAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 100)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String enumerationJson;

    @Column(length = 200)
    private String calculatedValue;

    @Column(columnDefinition = "TEXT")
    private String valueJson;

    @Column
    private Boolean recurring;

    @Column(columnDefinition = "TEXT")
    private String multiCategoryOptionsJson;
}
