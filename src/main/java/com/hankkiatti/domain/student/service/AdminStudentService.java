package com.hankkiatti.domain.student.service;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.common.PhoneNumbers;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.RequestCancelType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.service.MailOutboxService;
import com.hankkiatti.domain.student.dto.request.AdminStudentCreateRequestDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentApplicationResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCreateResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentDetailResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentIncidentResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentIncidentType;
import com.hankkiatti.domain.student.dto.response.AdminStudentInfoResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentMatchingResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentSummaryResponseDto;
import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.exception.StudentErrorType;
import com.hankkiatti.domain.student.exception.StudentException;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.domain.student.repository.StudentRecentRequestProjection;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private final MailOutboxService mailOutboxService;
    private final PasswordEncoder passwordEncoder;

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

    @Transactional(readOnly = true)
    public AdminStudentDetailResponseDto getStudent(Long adminAccountId, Long studentAccountId) {
        Admin admin = requireAdmin(adminAccountId);
        Student student = studentRepository.findWithAccountByAccountId(studentAccountId)
                .orElseThrow(() -> new StudentException(StudentErrorType.NOT_FOUND,
                        "studentAccountId=" + studentAccountId));
        List<HelpRequest> requests =
                helpRequestRepository.findByStudentAccountIdOrderByStartAtDescIdDesc(studentAccountId);
        Map<Long, List<Application>> applications = applicationsByRequest(requests);

        LocalDateTime recentRequestAt = requests.isEmpty() ? null : requests.get(0).getStartAt();
        return new AdminStudentDetailResponseDto(
                toInfo(student, recentRequestAt, admin.isFull()),
                requests.stream().map(request -> toMatching(request, applications.getOrDefault(
                        request.getId(), List.of()))).toList(),
                incidentHistory(requests, applications));
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

    private Map<Long, List<Application>> applicationsByRequest(List<HelpRequest> requests) {
        if (requests.isEmpty()) {
            return Map.of();
        }
        return applicationRepository.findWithHelperByHelpRequestIdIn(
                        requests.stream().map(HelpRequest::getId).toList()).stream()
                .collect(Collectors.groupingBy(application -> application.getHelpRequest().getId()));
    }

    private AdminStudentInfoResponseDto toInfo(Student student, LocalDateTime recentRequestAt,
                                               boolean fullAdmin) {
        Account account = student.getAccount();
        return new AdminStudentInfoResponseDto(
                student.getAccountId(),
                student.getName(),
                student.getStudentNo(),
                fullAdmin ? student.getPhone() : null,
                fullAdmin ? student.getKakaoId() : null,
                fullAdmin ? student.getSchoolEmail() : null,
                fullAdmin ? student.getDisabilityType() : null,
                fullAdmin ? student.getSpecialNote() : null,
                account.getStatus(),
                fullAdmin ? account.getLastLoginAt() : null,
                fullAdmin ? account.getDeactivatedAt() : null,
                fullAdmin ? student.getCredentialMailStatus() : null,
                fullAdmin ? student.getCredentialMailSentAt() : null,
                recentRequestAt);
    }

    private AdminStudentMatchingResponseDto toMatching(HelpRequest request, List<Application> applications) {
        return new AdminStudentMatchingResponseDto(
                request.getId(),
                request.getStartAt(),
                request.getEndAt(),
                request.getStatus(),
                request.getHelpTypes().stream().sorted().toList(),
                applications.stream().map(this::toApplication).toList());
    }

    private AdminStudentApplicationResponseDto toApplication(Application application) {
        return new AdminStudentApplicationResponseDto(
                application.getId(),
                application.getHelper().getAccountId(),
                application.getHelper().getName(),
                application.getHelper().getStudentNo(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getMatchedAt(),
                application.getCanceledAt());
    }

    private List<AdminStudentIncidentResponseDto> incidentHistory(
            List<HelpRequest> requests, Map<Long, List<Application>> applications) {
        List<AdminStudentIncidentResponseDto> incidents = new ArrayList<>();
        for (HelpRequest request : requests) {
            if (request.getStatus() == HelpRequestStatus.CANCELED) {
                incidents.add(requestCancellation(request));
            } else if (request.getStatus() == HelpRequestStatus.NO_SHOW) {
                incidents.add(new AdminStudentIncidentResponseDto(
                        AdminStudentIncidentType.NO_SHOW,
                        request.getId(),
                        null,
                        request.getStartAt(),
                        request.getNoShowReportedAt(),
                        null,
                        null,
                        null,
                        null));
            }
            applications.getOrDefault(request.getId(), List.of()).stream()
                    .filter(application -> application.getStatus() == ApplicationStatus.HELPER_CANCELED)
                    .map(this::helperCancellation)
                    .forEach(incidents::add);
        }
        incidents.sort(Comparator.comparing(
                        AdminStudentIncidentResponseDto::occurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AdminStudentIncidentResponseDto::helpRequestId, Comparator.reverseOrder()));
        return incidents;
    }

    private AdminStudentIncidentResponseDto requestCancellation(HelpRequest request) {
        return new AdminStudentIncidentResponseDto(
                toIncidentType(request.getCancelType()),
                request.getId(),
                null,
                request.getStartAt(),
                request.getCanceledAt(),
                null,
                null,
                null,
                null);
    }

    private AdminStudentIncidentResponseDto helperCancellation(Application application) {
        return new AdminStudentIncidentResponseDto(
                AdminStudentIncidentType.HELPER_CANCELED,
                application.getHelpRequest().getId(),
                application.getId(),
                application.getHelpRequest().getStartAt(),
                application.getCanceledAt(),
                application.getHelper().getName(),
                application.getHelper().getStudentNo(),
                application.getCancelReason(),
                application.getCancelReasonDetail());
    }

    private static AdminStudentIncidentType toIncidentType(RequestCancelType cancelType) {
        return switch (Objects.requireNonNull(cancelType)) {
            case STUDENT_WITHDRAW -> AdminStudentIncidentType.REQUEST_WITHDRAWN;
            case STUDENT_CANCEL -> AdminStudentIncidentType.STUDENT_CANCELED;
            case ACCOUNT_DEACTIVATED -> AdminStudentIncidentType.ACCOUNT_DEACTIVATED;
        };
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
