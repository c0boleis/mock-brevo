package org.enoria.mockbrevo.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactListRepository extends JpaRepository<ContactList, Long> {
    Page<ContactList> findByAccountOrderByIdAsc(Account account, Pageable pageable);

    Optional<ContactList> findByIdAndAccount(Long id, Account account);

    long countByAccount(Account account);
}
