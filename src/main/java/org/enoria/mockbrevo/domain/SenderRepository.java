package org.enoria.mockbrevo.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SenderRepository extends JpaRepository<Sender, Long> {
    List<Sender> findByAccountOrderByIdAsc(Account account);

    long countByAccount(Account account);
}
