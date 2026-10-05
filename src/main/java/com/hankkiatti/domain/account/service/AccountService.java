package com.hankkiatti.domain.account.service;

import com.hankkiatti.domain.account.dto.request.MeSettingsUpdateRequestDto;
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
        return toMeResponse(findAccount(accountId));
    }

    /**
     * 접근성 모드를 계정에 저장한다. 다른 기기에서 로그인해도 같은 설정으로 보이게 하려는 것.
     */
    @Transactional
    public MeResponseDto updateSettings(Long accountId, MeSettingsUpdateRequestDto request) {
        Account account = findAccount(accountId);
        account.changeAccessibilityMode(request.accessibilityMode());
        return toMeResponse(account);
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountException(AccountErrorType.NOT_FOUND, "accountId=" + accountId));
    }

    private MeResponseDto toMeResponse(Account account) {
        return new MeResponseDto(account.getId(), account.getLoginId(), account.getRole(), account.isAccessibilityMode());
    }
}
