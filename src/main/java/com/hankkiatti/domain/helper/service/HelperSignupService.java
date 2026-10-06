package com.hankkiatti.domain.helper.service;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.helper.dto.request.HelperSignupRequestDto;
import com.hankkiatti.domain.helper.dto.response.HelperSignupResponseDto;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.exception.HelperErrorType;
import com.hankkiatti.domain.helper.exception.HelperException;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도우미 회원가입. 승인 절차 없이 가입 즉시 로그인할 수 있다 (기능명세서 "도우미 회원가입").
 * 같은 이메일·학번이 동시에 들어오면 앞의 중복 확인을 둘 다 통과할 수 있다. 그때는 DB 유니크 제약이 뒤의 것을 막고,
 * 잠시 후 다시 보내라고 응답한다 — 다시 보내면 중복 확인에서 정확한 이유(이메일·학번 중복)를 알려 준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelperSignupService {

    private final AccountRepository accountRepository;
    private final HelperRepository helperRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public HelperSignupResponseDto signup(HelperSignupRequestDto request) {
        String loginId = Account.normalizeLoginId(request.email());
        String studentNo = request.studentNo().trim();

        if (accountRepository.existsByLoginId(loginId) || helperRepository.existsByEmail(loginId)) {
            throw new HelperException(HelperErrorType.DUPLICATE_EMAIL);
        }
        if (helperRepository.existsByStudentNo(studentNo)) {
            throw new HelperException(HelperErrorType.DUPLICATE_STUDENT_NO);
        }

        Account account;
        try {
            // 유니크 제약 위반을 여기서 잡으려고 바로 flush한다
            account = accountRepository.saveAndFlush(
                    new Account(loginId, passwordEncoder.encode(request.password()), AccountRole.HELPER, false, false));
            helperRepository.saveAndFlush(new Helper(
                    account,
                    request.name().trim(),
                    studentNo,
                    loginId,
                    normalizePhone(request.phone()),
                    request.kakaoId(),
                    Boolean.TRUE.equals(request.attiMember()),
                    LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException exception) {
            throw new HelperException(HelperErrorType.SIGNUP_CONFLICT);
        }

        // 이메일·전화번호는 로그에 남기지 않는다
        log.info("도우미 가입: accountId={}", account.getId());
        return new HelperSignupResponseDto(account.getId(), loginId);
    }

    // 010-1234-5678 / 01012345678 → 010-1234-5678 (요청 형식은 DTO가 이미 확인했다)
    private String normalizePhone(String raw) {
        String digits = raw.replace("-", "").trim();
        int middleEnd = digits.length() - 4;
        return digits.substring(0, 3) + "-" + digits.substring(3, middleEnd) + "-" + digits.substring(middleEnd);
    }
}
