package com.hankkiatti.domain.account.service;

import com.hankkiatti.domain.account.dto.request.MeContactUpdateRequestDto;
import com.hankkiatti.domain.account.dto.request.MeSettingsUpdateRequestDto;
import com.hankkiatti.domain.account.dto.response.MeHelperResponseDto;
import com.hankkiatti.domain.account.dto.response.MeResponseDto;
import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.exception.AccountErrorType;
import com.hankkiatti.domain.account.exception.AccountException;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.common.PhoneNumbers;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 장애학생·도우미의 내 정보(마이페이지). 장애 유형·특이사항은 관리자 API에서만 보여 주므로 여기 넣지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final StudentRepository studentRepository;
    private final HelperRepository helperRepository;
    private final ApplicationRepository applicationRepository;

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

    /**
     * 도우미가 자기 전화번호·카톡 ID를 바꾼다. 장애학생 연락처는 센터가 관리해 직접 바꿀 수 없다 (기능명세서 "마이페이지").
     */
    @Transactional
    public MeResponseDto updateContact(Long accountId, MeContactUpdateRequestDto request) {
        Account account = findAccount(accountId);
        if (account.getRole() != AccountRole.HELPER) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED, "도우미 전용, accountId=" + accountId);
        }
        findHelper(account).updateContact(PhoneNumbers.normalize(request.phone()), request.kakaoId());
        return toMeResponse(account);
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountException(AccountErrorType.NOT_FOUND, "accountId=" + accountId));
    }

    private MeResponseDto toMeResponse(Account account) {
        return switch (account.getRole()) {
            case STUDENT -> {
                Student student = studentRepository.findById(account.getId())
                        .orElseThrow(() -> profileNotFound(account));
                yield new MeResponseDto(account.getId(), account.getLoginId(), account.getRole(),
                        account.isAccessibilityMode(), student.getName(), student.getStudentNo(), student.getPhone(),
                        student.getKakaoId(), null);
            }
            case HELPER -> {
                Helper helper = findHelper(account);
                yield new MeResponseDto(account.getId(), account.getLoginId(), account.getRole(),
                        account.isAccessibilityMode(), helper.getName(), helper.getStudentNo(), helper.getPhone(),
                        helper.getKakaoId(),
                        new MeHelperResponseDto(helper.isAttiMember(), volunteerHoursOf(account)));
            }
            // 관리자는 /api/admin/me를 쓴다. 관리자 토큰은 SecurityConfig가 /api/**에서 막아 여기 오지 않는다
            case ADMIN -> throw new AuthException(AuthErrorType.ACCESS_DENIED, "관리자, accountId=" + account.getId());
        };
    }

    private Helper findHelper(Account account) {
        return helperRepository.findById(account.getId()).orElseThrow(() -> profileNotFound(account));
    }

    // 기록이 없으면 0.0. 소수 한 자리로 맞춘다 (봉사시간 컬럼과 같은 자릿수)
    private BigDecimal volunteerHoursOf(Account helperAccount) {
        BigDecimal sum = applicationRepository.sumVolunteerHoursByHelperId(helperAccount.getId());
        return (sum == null ? BigDecimal.ZERO : sum).setScale(1, RoundingMode.HALF_UP);
    }

    // 가입·장애학생 등록이 계정과 프로필을 함께 만들므로 정상 데이터에서는 일어나지 않는다
    private AccountException profileNotFound(Account account) {
        return new AccountException(AccountErrorType.NOT_FOUND, "프로필 없음, accountId=" + account.getId());
    }
}
