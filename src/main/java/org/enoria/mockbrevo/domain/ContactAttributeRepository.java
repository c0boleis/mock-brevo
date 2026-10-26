package org.enoria.mockbrevo.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactAttributeRepository extends JpaRepository<ContactAttribute, Long> {
    List<ContactAttribute> findByAccountOrderByCategoryAscNameAsc(Account account);

    Optional<ContactAttribute> findByAccountAndCategoryAndName(Account account, String category, String name);
}
