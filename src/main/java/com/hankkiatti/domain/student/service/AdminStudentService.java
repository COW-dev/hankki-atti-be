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
import com.hankkiatti.domain.auth.service.TemporaryPasswords;
import com.hankkiatti.domain.common.PersonalInfoMasks;
import com.hankkiatti.domain.common.PhoneNumbers;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.event.HelpRequestCanceledByStudentEvent;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.service.MailOutboxService;
import com.hankkiatti.domain.sms.entity.SmsConsentSource;
import com.hankkiatti.domain.sms.service.SmsConsentHistory;
import com.hankkiatti.domain.student.dto.request.AdminStudentCreateRequestDto;
import com.hankkiatti.domain.student.dto.request.AdminStudentUpdateRequestDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentAccountStatusResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCreateResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentDeactivationPreviewResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentInfoResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentSummaryResponseDto;
import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.exception.StudentErrorType;
import com.hankkiatti.domain.student.exception.StudentException;
import com.hankkiatti.domain.student.repository.StudentRecentRequestProjection;
import com.hankkiatti.domain.student.repository.StudentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStudentService {

    private static final String CREDENTIAL_MAIL_SUBJECT = "[한끼아띠] 장애학생 계정정보 안내";

    private final AdminRepository adminRepository;
    private final AccountRepository accountRepository;
    private final StudentRepository studentRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailOutboxService mailOutboxService;
    private final PasswordEncoder passwordEncoder;
    private final SmsConsentHistory smsConsentHistory;
    private final ApplicationEventPublisher eventPublisher;
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
        Admin admin = requireFullAdmin(adminAccountId);
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
        // 보내지 않으면 그대로. 실제로 바뀌었을 때만 이력을 남긴다
        if (request.smsConsent() != null
                && student.changeSmsConsent(request.smsConsent(), LocalDateTime.now(clock))) {
            smsConsentHistory.record(student.getAccount(), request.smsConsent(), SmsConsentSource.ADMIN, admin);
        }
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

    @Transactional(readOnly = true)
    public AdminStudentDeactivationPreviewResponseDto previewDeactivation(Long adminAccountId,
                                                                          Long studentAccountId) {
        requireFullAdmin(adminAccountId);
        Student student = studentRepository.findById(studentAccountId)
                .orElseThrow(() -> new StudentException(StudentErrorType.NOT_FOUND,
                        "studentAccountId=" + studentAccountId));
        long activeRequestCount = helpRequestRepository.countActiveBeforeMealByStudentAccountId(
                studentAccountId, LocalDateTime.now(clock));
        return new AdminStudentDeactivationPreviewResponseDto(
                studentAccountId, !student.getAccount().isActive(), activeRequestCount);
    }

    /**
     * 권한 확인(관리자 조회)이 잠금보다 먼저라 READ_COMMITTED로 둔다. REPEATABLE READ면 첫 조회 때 스냅샷이 잡혀,
     * 잠금을 기다리는 사이 이 장애학생이 새로 커밋한 신청을 취소 대상 조회에서 못 본다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminStudentAccountStatusResponseDto deactivate(Long adminAccountId, Long studentAccountId) {
        requireFullAdmin(adminAccountId);
        Account account = findStudentAccountForUpdate(studentAccountId);
        findStudentForUpdate(studentAccountId);

        LocalDateTime now = LocalDateTime.now(clock);
        // 식사가 시작된 신청은 취소하지 않는다 (장애학생 매칭 취소와 같은 기준, 2026-10-10 결정)
        List<Long> activeRequestIds =
                helpRequestRepository.findActiveIdsBeforeMealByStudentAccountId(studentAccountId, now);
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
            // 장애학생 매칭 취소와 같게 매칭·예비 도우미 모두에게 "학생 취소" 알림 (명세: 비활성화는 학생 취소와 같게, #37)
            if (!activeApplications.isEmpty()) {
                eventPublisher.publishEvent(new HelpRequestCanceledByStudentEvent(requestId,
                        helpRequest.getStartAt(), activeApplications.stream().map(Application::getId).toList()));
            }
        }

        account.deactivate(now);
        passwordResetTokenRepository.invalidateAllByAccountId(studentAccountId, now);
        log.info("장애학생 계정 비활성화: accountId={}, requests={}, applications={}",
                studentAccountId, activeRequestIds.size(), canceledApplications);
        return new AdminStudentAccountStatusResponseDto(
                account.getId(), account.getStatus(), account.getDeactivatedAt());
    }

    @Transactional
    public AdminStudentAccountStatusResponseDto activate(Long adminAccountId, Long studentAccountId) {
        requireFullAdmin(adminAccountId);
        Account account = findStudentAccountForUpdate(studentAccountId);
        findStudentForUpdate(studentAccountId);

        account.activate();
        log.info("장애학생 계정 재활성화: accountId={}", studentAccountId);
        return new AdminStudentAccountStatusResponseDto(
                account.getId(), account.getStatus(), account.getDeactivatedAt());
    }

    @Transactional
    public AdminStudentCreateResponseDto create(Long adminAccountId, AdminStudentCreateRequestDto request) {
        Admin admin = requireFullAdmin(adminAccountId);

        String studentNo = request.studentNo().trim();
        if (accountRepository.existsByLoginId(studentNo) || studentRepository.existsByStudentNo(studentNo)) {
            throw new StudentException(StudentErrorType.DUPLICATE_STUDENT_NO);
        }

        String temporaryPassword = TemporaryPasswords.generate();
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
        // 문자 수신 동의는 센터가 장애학생에게 받고 체크한다 (선택). 동의하지 않아도 첫 상태를 이력에 남긴다
        boolean smsConsent = Boolean.TRUE.equals(request.smsConsent());
        student.changeSmsConsent(smsConsent, LocalDateTime.now(clock));
        smsConsentHistory.record(account, smsConsent, SmsConsentSource.ADMIN, admin);
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
        String temporaryPassword = TemporaryPasswords.generate();
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

    private Admin requireFullAdmin(Long adminAccountId) {
        Admin admin = requireAdmin(adminAccountId);
        if (!admin.isFull()) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED,
                    "adminAccountId=" + adminAccountId + ", grade=" + admin.getGrade());
        }
        return admin;
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
                student.getSpecialNote(),
                student.getSmsConsentedAt());
    }

    private AdminStudentSummaryResponseDto toSummary(Student student, LocalDateTime recentRequestAt,
                                                      boolean fullAdmin) {
        Account account = student.getAccount();
        return new AdminStudentSummaryResponseDto(
                student.getAccountId(),
                student.getName(),
                student.getStudentNo(),
                fullAdmin ? student.getDisabilityType() : null,
                fullAdmin ? PersonalInfoMasks.email(student.getSchoolEmail()) : null,
                fullAdmin ? PhoneNumbers.mask(student.getPhone()) : null,
                fullAdmin ? PersonalInfoMasks.identifier(student.getKakaoId()) : null,
                account.getStatus(),
                recentRequestAt);
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

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
