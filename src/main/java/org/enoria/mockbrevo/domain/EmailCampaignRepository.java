package org.enoria.mockbrevo.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, Long> {
    Page<EmailCampaign> findByAccountOrderByIdDesc(Account account, Pageable pageable);

    Optional<EmailCampaign> findByIdAndAccount(Long id, Account account);

    long countByAccount(Account account);
}
