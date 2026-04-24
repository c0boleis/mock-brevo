package org.enoria.mockbrevo.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByApiKey(String apiKey);

    List<Account> findAllByOrderByCreatedAtAsc();
}
