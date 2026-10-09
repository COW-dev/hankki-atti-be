package com.hankkiatti.domain.student.service;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.common.PhoneNumbers;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.service.MailOutboxService;
import com.hankkiatti.domain.student.dto.request.AdminStudentCreateRequestDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCreateResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.exception.StudentErrorType;
import com.hankkiatti.domain.student.exception.StudentException;
import com.hankkiatti.domain.student.repository.StudentRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStudentService {

    private static final String CREDENTIAL_MAIL_SUBJECT = "[한끼아띠] 장애학생 계정정보 안내";
    private static final int TEMPORARY_PASSWORD_LENGTH = 12;
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIALS = "!@#$%^&*";
    private static final String PASSWORD_CHARS = LETTERS + DIGITS + SPECIALS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminRepository adminRepository;
    private final AccountRepository accountRepository;
    private final StudentRepository studentRepository;
    private final MailOutboxService mailOutboxService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AdminStudentCreateResponseDto create(Long adminAccountId, AdminStudentCreateRequestDto request) {
        requireFullAdmin(adminAccountId);

        String studentNo = request.studentNo().trim();
        if (accountRepository.existsByLoginId(studentNo) || studentRepository.existsByStudentNo(studentNo)) {
            throw new StudentException(StudentErrorType.DUPLICATE_STUDENT_NO);
        }

        String temporaryPassword = generateTemporaryPassword();
        Account account;
        Student student;
        try {
            account = accountRepository.saveAndFlush(new Account(
                    studentNo,
                    passwordEncoder.encode(temporaryPassword),
                    AccountRole.STUDENT,
                    true,
                    true));
            student = studentRepository.saveAndFlush(new Student(
                    account,
                    request.name().trim(),
                    studentNo,
                    PhoneNumbers.normalize(request.phone()),
                    request.kakaoId().trim(),
                    request.schoolEmail().trim().toLowerCase(Locale.ROOT),
                    request.disabilityType(),
                    trimToNull(request.specialNote())));
        } catch (DataIntegrityViolationException exception) {
            throw new StudentException(StudentErrorType.REGISTRATION_CONFLICT, "studentNo=" + studentNo);
        }

        mailOutboxService.enqueue(
                MailType.STUDENT_CREDENTIAL,
                student.getSchoolEmail(),
                CREDENTIAL_MAIL_SUBJECT,
                credentialMailBody(studentNo, temporaryPassword),
                account.getId());
        log.info("장애학생 등록: accountId={}, studentNo={}", account.getId(), studentNo);

        return new AdminStudentCreateResponseDto(
                account.getId(),
                account.getLoginId(),
                student.getName(),
                student.getStudentNo(),
                student.getSchoolEmail(),
                student.getDisabilityType(),
                student.getCredentialMailStatus());
    }

    @Transactional
    public AdminStudentCredentialMailResponseDto retryCredentialMail(Long adminAccountId, Long studentAccountId) {
        requireFullAdmin(adminAccountId);

        Account account = accountRepository.findByIdForUpdate(studentAccountId)
                .filter(found -> found.getRole() == AccountRole.STUDENT)
                .orElseThrow(() -> new StudentException(StudentErrorType.NOT_FOUND,
                        "studentAccountId=" + studentAccountId));
        Student student = studentRepository.findByIdForUpdate(studentAccountId)
                .orElseThrow(() -> new StudentException(StudentErrorType.NOT_FOUND,
                        "studentAccountId=" + studentAccountId));
        if (student.getCredentialMailStatus() != CredentialMailStatus.FAILED) {
            throw new StudentException(StudentErrorType.CREDENTIAL_MAIL_NOT_FAILED,
                    "studentAccountId=" + studentAccountId + ", status=" + student.getCredentialMailStatus());
        }

        String temporaryPassword = generateTemporaryPassword();
        account.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword));
        student.markCredentialMailPending();
        mailOutboxService.enqueue(
                MailType.STUDENT_CREDENTIAL,
                student.getSchoolEmail(),
                CREDENTIAL_MAIL_SUBJECT,
                credentialMailBody(account.getLoginId(), temporaryPassword),
                account.getId());
        log.info("장애학생 계정정보 메일 재발송 요청: accountId={}", account.getId());

        return new AdminStudentCredentialMailResponseDto(
                account.getId(), account.getLoginId(), student.getSchoolEmail(), student.getCredentialMailStatus());
    }

    private void requireFullAdmin(Long adminAccountId) {
        Admin admin = adminRepository.findById(adminAccountId)
                .orElseThrow(() -> new AuthException(AuthErrorType.ACCESS_DENIED, "adminAccountId=" + adminAccountId));
        if (!admin.isFull()) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED,
                    "adminAccountId=" + adminAccountId + ", grade=" + admin.getGrade());
        }
    }

    private String credentialMailBody(String loginId, String temporaryPassword) {
        return """
                한끼아띠 계정이 발급되었습니다.
                아래 계정정보로 로그인한 뒤 비밀번호를 변경해 주세요.

                아이디: %s
                초기 비밀번호: %s

                본인이 요청하지 않은 계정이거나 문의가 필요하면 명지대학교 장애학생지원센터로 연락해 주세요.
                """.formatted(loginId, temporaryPassword);
    }

    private static String generateTemporaryPassword() {
        List<Character> characters = new ArrayList<>();
        characters.add(randomChar(LETTERS));
        characters.add(randomChar(DIGITS));
        characters.add(randomChar(SPECIALS));
        while (characters.size() < TEMPORARY_PASSWORD_LENGTH) {
            characters.add(randomChar(PASSWORD_CHARS));
        }
        Collections.shuffle(characters, RANDOM);

        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        characters.forEach(password::append);
        return password.toString();
    }

    private static char randomChar(String candidates) {
        return candidates.charAt(RANDOM.nextInt(candidates.length()));
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
