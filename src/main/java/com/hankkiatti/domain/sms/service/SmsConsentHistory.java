package com.hankkiatti.domain.sms.service;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.sms.entity.SmsConsentChange;
import com.hankkiatti.domain.sms.entity.SmsConsentSource;
import com.hankkiatti.domain.sms.repository.SmsConsentChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 문자 수신 동의 이력을 남긴다. 부르는 쪽의 트랜잭션에서 저장한다 — 동의 상태 변경과 함께 남거나 함께 없다.
 */
@Component
@RequiredArgsConstructor
public class SmsConsentHistory {

    private final SmsConsentChangeRepository smsConsentChangeRepository;

    public void record(Account account, boolean consented, SmsConsentSource source, Admin admin) {
        smsConsentChangeRepository.save(new SmsConsentChange(account, consented, source, admin));
    }
}
