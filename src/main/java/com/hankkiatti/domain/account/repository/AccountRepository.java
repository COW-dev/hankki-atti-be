package com.hankkiatti.domain.account.repository;

import com.hankkiatti.domain.account.entity.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
