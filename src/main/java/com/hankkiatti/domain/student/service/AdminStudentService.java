package com.hankkiatti.domain.student.service;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.auth.repository.PasswordResetTokenRepository;
import com.hankkiatti.domain.common.PhoneNumbers;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.service.MailOutboxService;
import com.hankkiatti.domain.student.dto.request.AdminStudentCreateRequestDto;
import com.hankkiatti.domain.student.dto.request.AdminStudentUpdateRequestDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentAccountStatusResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCreateResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentInfoResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentSummaryResponseDto;
import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.exception.StudentErrorType;
import com.hankkiatti.domain.student.exception.StudentException;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.domain.student.repository.StudentRecentRequestProjection;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final HelpRequestRepository helpRequestRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailOutboxService mailOutboxService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AdminStudentSummaryResponseDto> getStudents(Long adminAccountId, String keyword,
                                                            DisabilityType disabilityType, AccountStatus status) {
        Admin admin = requireAdmin(adminAccountId);
        if (!admin.isFull() && disabilityType != null) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED,
                    "제한 권한의 장애 유형 필터 요청, adminAccountId=" + adminAccountId);
        }

        List<Student> students = studentRepository.search(trimToNull(keyword), disabilityType, status);
        Map<Long, LocalDateTime> recentRequestAt = recentRequestAt(students);
        return students.stream()
                .map(student -> toSummary(student, recentRequestAt.get(student.getAccountId()), admin.isFull()))
                .toList();
    }

    @Transactional
    public AdminStudentInfoResponseDto update(Long adminAccountId, Long studentAccountId,
                                              AdminStudentUpdateRequestDto request) {
        requireFullAdmin(adminAccountId);
        Student student = studentRepository.findByIdForUpdate(studentAccountId)
                .orElseThrow(() -> new StudentException(StudentErrorType.NOT_FOUND,
                        "studentAccountId=" + studentAccountId));
        student.updateInfo(
                request.name().trim(),
                PhoneNumbers.normalize(request.phone()),
                request.kakaoId().trim(),
                request.schoolEmail().trim().toLowerCase(Locale.ROOT),
                request.disabilityType(),
                trimToNull(request.specialNote()));

        log.info("장애학생 정보 수정: accountId={}", studentAccountId);
        return toInfo(student);
    }

    @Transactional
    public AdminStudentCredentialMailResponseDto resendCredentialMail(Long adminAccountId, Long studentAccountId) {
        requireFullAdmin(adminAccountId);
        Account account = findStudentAccountForUpdate(studentAccountId);
        Student student = findStudentForUpdate(studentAccountId);
        if (student.getCredentialMailStatus() == CredentialMailStatus.PENDING) {
            throw new StudentException(StudentErrorType.CREDENTIAL_MAIL_PENDING,
                    "studentAccountId=" + studentAccountId);
        }

        issueCredentialMail(account, student);
        log.info("장애학생 계정정보 재발송 요청: accountId={}", account.getId());
        return toCredentialMailResponse(account, student);
    }

    @Transactional
    public AdminStudentAccountStatusResponseDto deactivate(Long adminAccountId, Long studentAccountId) {
        requireFullAdmin(adminAccountId);
        Account account = findStudentAccountForUpdate(studentAccountId);
        findStudentForUpdate(studentAccountId);

        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> activeRequestIds = helpRequestRepository.findActiveIdsByStudentAccountId(studentAccountId);
        int canceledApplications = 0;
        for (Long requestId : activeRequestIds) {
            HelpRequest helpRequest = helpRequestRepository.findByIdForUpdate(requestId).orElse(null);
            if (helpRequest == null || !helpRequest.getStatus().isInProgress()) {
                continue;
            }
            List<Application> activeApplications =
                    applicationRepository.findActiveForUpdate(requestId);
            activeApplications.forEach(Application::cancelByStudent);
            canceledApplications += activeApplications.size();
            helpRequest.cancelByDeactivation(now);
        }

        account.deactivate(now);
        passwordResetTokenRepository.invalidateAllByAccountId(studentAccountId, now);
        log.info("장애학생 계정 비활성화: accountId={}, requests={}, applications={}",
                studentAccountId, activeRequestIds.size(), canceledApplications);
        return new AdminStudentAccountStatusResponseDto(
                account.getId(), account.getStatus(), account.getDeactivatedAt());
    }

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
            // 학번은 남기지 않는다 — "이 학번은 장애학생"이 로그에 남는다
            throw new StudentException(StudentErrorType.REGISTRATION_CONFLICT, "학번·아이디 중복");
        }

        mailOutboxService.enqueue(
                MailType.STUDENT_CREDENTIAL,
                student.getSchoolEmail(),
                CREDENTIAL_MAIL_SUBJECT,
                credentialMailBody(studentNo, temporaryPassword),
                account.getId());
        log.info("장애학생 등록: accountId={}", account.getId());

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

        Account account = findStudentAccountForUpdate(studentAccountId);
        Student student = findStudentForUpdate(studentAccountId);
        if (student.getCredentialMailStatus() != CredentialMailStatus.FAILED) {
            throw new StudentException(StudentErrorType.CREDENTIAL_MAIL_NOT_FAILED,
                    "studentAccountId=" + studentAccountId + ", status=" + student.getCredentialMailStatus());
        }

        issueCredentialMail(account, student);
        log.info("장애학생 계정정보 메일 재발송 요청: accountId={}", account.getId());

        return toCredentialMailResponse(account, student);
    }

    private Account findStudentAccountForUpdate(Long studentAccountId) {
        return accountRepository.findByIdForUpdate(studentAccountId)
                .filter(found -> found.getRole() == AccountRole.STUDENT)
                .orElseThrow(() -> new StudentException(StudentErrorType.NOT_FOUND,
                        "studentAccountId=" + studentAccountId));
    }

    private Student findStudentForUpdate(Long studentAccountId) {
        return studentRepository.findByIdForUpdate(studentAccountId)
                .orElseThrow(() -> new StudentException(StudentErrorType.NOT_FOUND,
                        "studentAccountId=" + studentAccountId));
    }

    private void issueCredentialMail(Account account, Student student) {
        String temporaryPassword = generateTemporaryPassword();
        account.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword));
        student.markCredentialMailPending();
        passwordResetTokenRepository.invalidateAllByAccountId(account.getId(), LocalDateTime.now(clock));
        mailOutboxService.enqueue(
                MailType.STUDENT_CREDENTIAL,
                student.getSchoolEmail(),
                CREDENTIAL_MAIL_SUBJECT,
                credentialMailBody(account.getLoginId(), temporaryPassword),
                account.getId());
    }

    private AdminStudentCredentialMailResponseDto toCredentialMailResponse(Account account, Student student) {
        return new AdminStudentCredentialMailResponseDto(
                account.getId(), account.getLoginId(), student.getSchoolEmail(), student.getCredentialMailStatus());
    }

    private void requireFullAdmin(Long adminAccountId) {
        Admin admin = requireAdmin(adminAccountId);
        if (!admin.isFull()) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED,
                    "adminAccountId=" + adminAccountId + ", grade=" + admin.getGrade());
        }
    }

    private Admin requireAdmin(Long adminAccountId) {
        return adminRepository.findById(adminAccountId)
                .orElseThrow(() -> new AuthException(AuthErrorType.ACCESS_DENIED,
                        "adminAccountId=" + adminAccountId));
    }

    private Map<Long, LocalDateTime> recentRequestAt(List<Student> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        return helpRequestRepository.findRecentRequestAtByStudentIds(
                        students.stream().map(Student::getAccountId).toList()).stream()
                .collect(Collectors.toMap(StudentRecentRequestProjection::getStudentAccountId,
                        StudentRecentRequestProjection::getRecentRequestAt));
    }

    private AdminStudentInfoResponseDto toInfo(Student student) {
        Account account = student.getAccount();
        return new AdminStudentInfoResponseDto(
                student.getAccountId(),
                student.getName(),
                student.getStudentNo(),
                account.getStatus(),
                student.getDisabilityType(),
                student.getPhone(),
                student.getSchoolEmail(),
                student.getKakaoId(),
                account.getLoginId(),
                account.isAccessibilityMode(),
                student.getCreatedAt(),
                student.getCredentialMailStatus(),
                student.getSpecialNote());
    }

    private AdminStudentSummaryResponseDto toSummary(Student student, LocalDateTime recentRequestAt,
                                                      boolean fullAdmin) {
        Account account = student.getAccount();
        return new AdminStudentSummaryResponseDto(
                student.getAccountId(),
                student.getName(),
                student.getStudentNo(),
                fullAdmin ? student.getDisabilityType() : null,
                fullAdmin ? maskEmail(student.getSchoolEmail()) : null,
                fullAdmin ? maskPhone(student.getPhone()) : null,
                fullAdmin ? maskIdentifier(student.getKakaoId()) : null,
                account.getStatus(),
                recentRequestAt);
    }

    private static String maskEmail(String email) {
        int separator = email.indexOf('@');
        if (separator <= 0) {
            return maskIdentifier(email);
        }
        return maskIdentifier(email.substring(0, separator)) + email.substring(separator);
    }

    private static String maskPhone(String phone) {
        int firstSeparator = phone.indexOf('-');
        int lastSeparator = phone.lastIndexOf('-');
        if (firstSeparator < 0 || firstSeparator == lastSeparator) {
            return maskIdentifier(phone);
        }
        return phone.substring(0, firstSeparator + 1)
                + "*".repeat(lastSeparator - firstSeparator - 1)
                + phone.substring(lastSeparator);
    }

    private static String maskIdentifier(String value) {
        int visibleLength = Math.min(2, value.length());
        int maskedLength = Math.max(3, value.length() - visibleLength);
        return value.substring(0, visibleLength) + "*".repeat(maskedLength);
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
