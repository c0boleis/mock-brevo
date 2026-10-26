package org.enoria.mockbrevo.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    Page<Folder> findByAccountOrderByIdAsc(Account account, Pageable pageable);

    Optional<Folder> findByIdAndAccount(Long id, Account account);

    long countByAccount(Account account);
}
