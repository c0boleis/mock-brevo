package org.enoria.mockbrevo.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    Optional<Contact> findByAccountAndEmail(Account account, String email);

    List<Contact> findByAccountAndEmailIn(Account account, List<String> emails);

    Page<Contact> findByAccountAndListsContainingOrderByIdAsc(Account account, ContactList list, Pageable pageable);

    long countByAccount(Account account);

    long countByAccountAndListsContaining(Account account, ContactList list);
}
