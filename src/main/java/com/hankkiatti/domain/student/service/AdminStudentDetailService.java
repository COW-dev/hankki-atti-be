package com.hankkiatti.domain.student.service;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.RequestCancelType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.dto.response.AdminStudentHeader;
import com.hankkiatti.domain.student.dto.response.AdminStudentHeaderResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHelpRequestResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHelpRequestsResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHelperResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHistoriesResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHistoryResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHistoryType;
import com.hankkiatti.domain.student.dto.response.AdminStudentInfoResponseDto;
import com.hankkiatti.domain.student.dto.response.LimitedAdminStudentHeaderResponseDto;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.exception.StudentErrorType;
import com.hankkiatti.domain.student.exception.StudentException;
import com.hankkiatti.domain.student.repository.StudentRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 장애학생 상세 3탭 (Notion 기능명세서 "장애학생 관리", Figma AF-02 상세). 조회만.
 * 정보 탭은 전체 권한만 (제한 권한 403). 매칭현황·취소·노쇼 이력은 두 등급 모두 보고, 위 프로필의 장애 유형은 전체 권한만 넣는다.
 */
@Service
@RequiredArgsConstructor
public class AdminStudentDetailService {

    // 장애학생에게 도우미가 확정된 지원 (매칭 완료·이용 완료·노쇼)
    private static final Set<ApplicationStatus> CONFIRMED_HELPER = Set.of(
            ApplicationStatus.MATCHED, ApplicationStatus.COMPLETED, ApplicationStatus.NO_SHOW);
    private static final Comparator<HelpRequest> LATEST_MEAL_FIRST =
            Comparator.comparing(HelpRequest::getStartAt).thenComparing(HelpRequest::getId).reversed();

    private final AdminRepository adminRepository;
    private final StudentRepository studentRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final ApplicationRepository applicationRepository;

    /**
     * 정보 탭 — 연락처·장애 유형·특이사항까지 보여 주므로 전체 권한만.
     */
    @Transactional(readOnly = true)
    public AdminStudentInfoResponseDto getInfo(Long adminAccountId, Long studentAccountId) {
        Admin admin = requireAdmin(adminAccountId);
        if (!admin.isFull()) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED,
                    "정보 탭은 전체 권한만, adminAccountId=" + adminAccountId);
        }
        Student student = findStudent(studentAccountId);
        Account account = student.getAccount();
        return new AdminStudentInfoResponseDto(student.getAccountId(), student.getName(), student.getStudentNo(),
                account.getStatus(), student.getDisabilityType(), student.getPhone(), student.getSchoolEmail(),
                student.getKakaoId(), account.getLoginId(), account.isAccessibilityMode(), student.getCreatedAt(),
                student.getCredentialMailStatus(), student.getSpecialNote());
    }

    /**
     * 매칭현황 탭 — 그 학생의 신청을 최근 식사부터. 쿼리 2번(신청, 지원 + 도우미).
     */
    @Transactional(readOnly = true)
    public AdminStudentHelpRequestsResponseDto getHelpRequests(Long adminAccountId, Long studentAccountId) {
        Admin admin = requireAdmin(adminAccountId);
        Student student = findStudent(studentAccountId);
        List<HelpRequest> requests = latestFirst(studentAccountId);
        Map<Long, List<Application>> applications = applicationsByRequest(requests);

        List<AdminStudentHelpRequestResponseDto> rows = requests.stream()
                .map(request -> toHelpRequestRow(request, applications.getOrDefault(request.getId(), List.of())))
                .toList();
        return new AdminStudentHelpRequestsResponseDto(toHeader(admin, student), rows);
    }

    /**
     * 취소·노쇼 이력 탭 — 도우미 취소, 장애학생 매칭 취소, 노쇼 신고를 최근 식사부터. 신청 철회(모집 중 취소)는 매칭이 없던 신청이라 넣지 않는다.
     * 패널티는 센터가 이 이력을 보고 사후에 판단한다 (시스템에서 적용하지 않음).
     */
    @Transactional(readOnly = true)
    public AdminStudentHistoriesResponseDto getHistories(Long adminAccountId, Long studentAccountId) {
        Admin admin = requireAdmin(adminAccountId);
        Student student = findStudent(studentAccountId);
        List<HelpRequest> requests = latestFirst(studentAccountId);
        Map<Long, List<Application>> applications = applicationsByRequest(requests);

        List<AdminStudentHistoryResponseDto> histories = new ArrayList<>();
        for (HelpRequest request : requests) {
            List<Application> ofRequest = applications.getOrDefault(request.getId(), List.of());
            ofRequest.stream()
                    .filter(application -> application.getStatus() == ApplicationStatus.HELPER_CANCELED)
                    .sorted(Comparator.comparing(Application::getCanceledAt).reversed())
                    .forEach(canceled -> histories.add(helperCanceled(request, canceled, ofRequest)));
            if (request.getCancelType() == RequestCancelType.STUDENT_CANCEL) {
                histories.add(studentCanceled(request));
            }
            if (request.getStatus() == HelpRequestStatus.NO_SHOW) {
                histories.add(noShow(request, ofRequest));
            }
        }
        return new AdminStudentHistoriesResponseDto(toHeader(admin, student), histories);
    }

    private Admin requireAdmin(Long adminAccountId) {
        return adminRepository.findById(adminAccountId)
                .orElseThrow(() -> new AuthException(AuthErrorType.ACCESS_DENIED, "adminAccountId=" + adminAccountId));
    }

    private Student findStudent(Long studentAccountId) {
        return studentRepository.findById(studentAccountId)
                .orElseThrow(() -> new StudentException(StudentErrorType.NOT_FOUND,
                        "studentAccountId=" + studentAccountId));
    }

    private List<HelpRequest> latestFirst(Long studentAccountId) {
        return helpRequestRepository.findByStudentAccountId(studentAccountId).stream()
                .sorted(LATEST_MEAL_FIRST)
                .toList();
    }

    private Map<Long, List<Application>> applicationsByRequest(List<HelpRequest> requests) {
        if (requests.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = requests.stream().map(HelpRequest::getId).toList();
        return applicationRepository.findWithHelperByHelpRequestIdIn(ids).stream()
                .collect(Collectors.groupingBy(application -> application.getHelpRequest().getId()));
    }

    private static AdminStudentHeader toHeader(Admin admin, Student student) {
        if (admin.isFull()) {
            return new AdminStudentHeaderResponseDto(student.getAccountId(), student.getName(), student.getStudentNo(),
                    student.getAccount().getStatus(), student.getDisabilityType());
        }
        return new LimitedAdminStudentHeaderResponseDto(student.getAccountId(), student.getName(),
                student.getStudentNo(), student.getAccount().getStatus());
    }

    private static AdminStudentHelpRequestResponseDto toHelpRequestRow(HelpRequest request,
                                                                     List<Application> applications) {
        // 한 신청에 확정 지원이 여럿이면(이용 완료 뒤 노쇼 등) 가장 최근 지원
        Application confirmed = applications.stream()
                .filter(application -> CONFIRMED_HELPER.contains(application.getStatus()))
                .max(Comparator.comparing(Application::getId))
                .orElse(null);
        boolean promotionPending = applications.stream()
                .anyMatch(application -> application.getStatus() == ApplicationStatus.PROMOTION_PENDING);
        return new AdminStudentHelpRequestResponseDto(request.getId(), request.getStartAt(), request.getEndAt(),
                request.getStatus(), request.getHelpTypes().stream().sorted().toList(), request.getCancelType(),
                confirmed == null ? null : toHelper(confirmed.getHelper()),
                confirmed == null ? null : confirmed.getPromotedWaitingOrder(),
                confirmed != null && confirmed.getPromotedAt() != null,
                promotionPending, request.isHelperChanged(), request.getCreatedAt(), request.getFirstMatchedAt());
    }

    // 도우미 취소. 예비 승격이면 같은 시각(취소와 같은 트랜잭션)에 승격된 지원이 그 뒤를 이은 도우미다
    private static AdminStudentHistoryResponseDto helperCanceled(HelpRequest request, Application canceled,
                                                                 List<Application> ofRequest) {
        Application promoted = canceled.getAfterAction() != ApplicationAfterAction.PROMOTED
                ? null
                : ofRequest.stream()
                        .filter(application -> Objects.equals(application.getPromotedAt(), canceled.getCanceledAt()))
                        .findFirst()
                        .orElse(null);
        return new AdminStudentHistoryResponseDto(AdminStudentHistoryType.HELPER_CANCELED, request.getId(),
                request.getStartAt(), toHelper(canceled.getHelper()), canceled.getCancelReason(),
                canceled.getCancelReasonDetail(), canceled.getCanceledAt(),
                minutesBetween(canceled.getCanceledAt(), request.getStartAt()), null, canceled.getAfterAction(),
                promoted == null ? null : toHelper(promoted.getHelper()),
                promoted == null ? null : promoted.getPromotedWaitingOrder(), request.getStatus());
    }

    private static AdminStudentHistoryResponseDto studentCanceled(HelpRequest request) {
        return new AdminStudentHistoryResponseDto(AdminStudentHistoryType.STUDENT_CANCELED, request.getId(),
                request.getStartAt(), null, null, null, request.getCanceledAt(),
                minutesBetween(request.getCanceledAt(), request.getStartAt()), null, null, null, null,
                request.getStatus());
    }

    private static AdminStudentHistoryResponseDto noShow(HelpRequest request, List<Application> ofRequest) {
        Helper helper = ofRequest.stream()
                .filter(application -> application.getStatus() == ApplicationStatus.NO_SHOW)
                .map(Application::getHelper)
                .findFirst()
                .orElse(null);
        return new AdminStudentHistoryResponseDto(AdminStudentHistoryType.NO_SHOW, request.getId(),
                request.getStartAt(), helper == null ? null : toHelper(helper), null, null,
                request.getNoShowReportedAt(), null,
                minutesBetween(request.getCompletedAt(), request.getNoShowReportedAt()), null, null, null,
                request.getStatus());
    }

    private static AdminStudentHelperResponseDto toHelper(Helper helper) {
        return new AdminStudentHelperResponseDto(helper.getAccountId(), helper.getName());
    }

    private static Long minutesBetween(LocalDateTime from, LocalDateTime to) {
        return from == null || to == null ? null : Duration.between(from, to).toMinutes();
    }
}
