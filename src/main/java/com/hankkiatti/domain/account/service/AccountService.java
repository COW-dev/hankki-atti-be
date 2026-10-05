package com.hankkiatti.domain.account.service;

import com.hankkiatti.domain.account.dto.response.MeResponseDto;
import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.exception.AccountErrorType;
import com.hankkiatti.domain.account.exception.AccountException;
import com.hankkiatti.domain.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public MeResponseDto getMe(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountException(AccountErrorType.NOT_FOUND, "accountId=" + accountId));
        return new MeResponseDto(account.getId(), account.getLoginId(), account.getRole(), account.isAccessibilityMode());
    }
}
