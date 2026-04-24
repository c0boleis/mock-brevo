package org.enoria.mockbrevo.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {
    Page<SentEmail> findByAccountOrderByIdDesc(Account account, Pageable pageable);

    long countByAccount(Account account);
}
