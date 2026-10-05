package com.hankkiatti.domain.admin.service;

import com.hankkiatti.domain.account.exception.AccountErrorType;
import com.hankkiatti.domain.account.exception.AccountException;
import com.hankkiatti.domain.admin.dto.response.AdminMeResponseDto;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;

    @Transactional(readOnly = true)
    public AdminMeResponseDto getMe(Long accountId) {
        Admin admin = adminRepository.findById(accountId)
                .orElseThrow(() -> new AccountException(AccountErrorType.NOT_FOUND, "adminAccountId=" + accountId));
        return new AdminMeResponseDto(
                admin.getAccountId(), admin.getAccount().getLoginId(), admin.getName(), admin.getGrade());
    }
}
