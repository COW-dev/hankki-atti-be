package com.hankkiatti.domain.sms.repository;

import com.hankkiatti.domain.sms.entity.SmsConsentChange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsConsentChangeRepository extends JpaRepository<SmsConsentChange, Long> {

    List<SmsConsentChange> findByAccountIdOrderByIdAsc(Long accountId);
}
