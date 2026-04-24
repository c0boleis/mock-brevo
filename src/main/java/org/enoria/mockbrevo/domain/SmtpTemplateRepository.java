package org.enoria.mockbrevo.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmtpTemplateRepository extends JpaRepository<SmtpTemplate, Long> {
    Page<SmtpTemplate> findByAccountAndActiveOrderByIdAsc(Account account, boolean active, Pageable pageable);

    Page<SmtpTemplate> findByAccountOrderByIdAsc(Account account, Pageable pageable);

    long countByAccount(Account account);
}
