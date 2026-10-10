package com.hankkiatti.domain.helper.service;

import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.repository.HelperApplicationCount;
import com.hankkiatti.domain.common.PhoneNumbers;
import com.hankkiatti.domain.helper.dto.response.AdminHelperActivityResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDetailResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperSummaryResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelpersResponseDto;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.exception.HelperErrorType;
import com.hankkiatti.domain.helper.exception.HelperException;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관리자 도우미 목록·상세·활동 이력 (Notion 기능명세서 "도우미 관리", Figma AF-03). 조회만, 전체·제한 권한 같은 응답.
 * 도우미 정보 수정은 도우미 본인만 한다 (관리자 수정 API 없음).
 */
@Service
@RequiredArgsConstructor
public class AdminHelperService {

    public static final int PAGE_SIZE = 20;

    private static final Comparator<Application> LATEST_MEAL_FIRST = Comparator
            .comparing((Application application) -> application.getHelpRequest().getStartAt())
            .thenComparing(Application::getId)
            .reversed();

    private final HelperRepository helperRepository;
    private final ApplicationRepository applicationRepository;

    /**
     * 목록 — 이름·학번 검색, 계정 상태·아띠 소속 필터, 가입 최근순 20명씩. 전화번호는 가린다 (전체 번호는 상세에서).
     */
    @Transactional(readOnly = true)
    public AdminHelpersResponseDto getHelpers(String q, AccountStatus status, Boolean attiMember, int page) {
        if (page < 0) {
            throw new HelperException(HelperErrorType.INVALID_PAGE, "page=" + page);
        }
        String pattern = StringUtils.hasText(q) ? "%" + q.trim() + "%" : null;
        Page<Helper> helpers = helperRepository.searchForAdmin(pattern, status, attiMember,
                PageRequest.of(page, PAGE_SIZE));

        Map<Long, Long> completedCounts = helpers.isEmpty()
                ? Map.of()
                : applicationRepository.countCompletedByHelper(helpers.map(Helper::getAccountId).toList()).stream()
                        .collect(Collectors.toMap(HelperApplicationCount::helperId, HelperApplicationCount::count));

        List<AdminHelperResponseDto> rows = helpers.stream()
                .map(helper -> new AdminHelperResponseDto(helper.getAccountId(), helper.getName(),
                        helper.getStudentNo(), helper.getEmail(), PhoneNumbers.mask(helper.getPhone()),
                        helper.getKakaoId(), helper.isAttiMember(), helper.getCreatedAt(),
                        helper.getAccount().getStatus(), completedCounts.getOrDefault(helper.getAccountId(), 0L)))
                .toList();
        return new AdminHelpersResponseDto(rows, page, PAGE_SIZE, helpers.getTotalElements(), helpers.getTotalPages());
    }

    /**
     * 상세 — 프로필과 활동 요약(매칭·이용 완료·봉사시간 합계·취소·노쇼).
     */
    @Transactional(readOnly = true)
    public AdminHelperDetailResponseDto getHelper(Long helperId) {
        Helper helper = findHelper(helperId);
        List<Application> applications = applicationRepository.findByHelperAccountId(helperId);
        return new AdminHelperDetailResponseDto(helper.getAccountId(), helper.getName(), helper.getStudentNo(),
                helper.getAccount().getStatus(), helper.isAttiMember(), helper.getEmail(), helper.getPhone(),
                helper.getKakaoId(), helper.getCreatedAt(), summarize(applications));
    }

    /**
     * 활동 이력 — 매칭되거나 승격된 지원을 최근 식사부터. 예비로만 있다가 빠진 지원(빠짐·자동 제외·예비 종료·예비 중 학생 취소)은 넣지 않는다.
     */
    @Transactional(readOnly = true)
    public List<AdminHelperActivityResponseDto> getActivities(Long helperId) {
        findHelper(helperId);
        List<Application> activities = applicationRepository.findMineWithHelpRequest(helperId).stream()
                .filter(AdminHelperService::wasMatchedOrPromoted)
                .sorted(LATEST_MEAL_FIRST)
                .toList();
        Map<Long, List<Application>> promotionsByRequest = promotionsAfterCancel(activities);

        return activities.stream()
                .map(application -> toActivity(application,
                        promotionsByRequest.getOrDefault(application.getHelpRequest().getId(), List.of())))
                .toList();
    }

    private Helper findHelper(Long helperId) {
        return helperRepository.findById(helperId)
                .orElseThrow(() -> new HelperException(HelperErrorType.NOT_FOUND, "helperId=" + helperId));
    }

    private static AdminHelperSummaryResponseDto summarize(List<Application> applications) {
        long matched = applications.stream().filter(application -> application.getMatchedAt() != null).count();
        long completed = countStatus(applications, ApplicationStatus.COMPLETED);
        long noShow = countStatus(applications, ApplicationStatus.NO_SHOW);
        // 관리자 비활성화로 생긴 취소는 도우미 탓이 아니라 넣지 않는다 (명세: 패널티 이력에 넣지 않음)
        long canceled = applications.stream()
                .filter(application -> application.getStatus() == ApplicationStatus.HELPER_CANCELED)
                .filter(application -> application.getCancelReason() != CancelReason.ADMIN_DEACTIVATED)
                .count();
        BigDecimal volunteerHours = applications.stream()
                .map(Application::getVolunteerHours)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AdminHelperSummaryResponseDto(matched, completed, volunteerHours, canceled, noShow);
    }

    private static long countStatus(List<Application> applications, ApplicationStatus status) {
        return applications.stream().filter(application -> application.getStatus() == status).count();
    }

    private static boolean wasMatchedOrPromoted(Application application) {
        return application.getMatchedAt() != null || application.getPromotedAt() != null;
    }

    // 예비 승격으로 끝난 취소 건 신청들의 지원. 승격된 예비를 찾는 데 쓴다
    private Map<Long, List<Application>> promotionsAfterCancel(List<Application> activities) {
        List<Long> requestIds = activities.stream()
                .filter(application -> application.getAfterAction() == ApplicationAfterAction.PROMOTED)
                .map(application -> application.getHelpRequest().getId())
                .distinct()
                .toList();
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        return applicationRepository.findWithHelperByHelpRequestIdIn(requestIds).stream()
                .filter(application -> application.getPromotedAt() != null)
                .collect(Collectors.groupingBy(application -> application.getHelpRequest().getId()));
    }

    private static AdminHelperActivityResponseDto toActivity(Application application, List<Application> promotions) {
        HelpRequest request = application.getHelpRequest();
        ApplicationStatus status = application.getStatus();
        boolean helperCanceled = status == ApplicationStatus.HELPER_CANCELED;
        LocalDateTime canceledAt = status == ApplicationStatus.STUDENT_CANCELED
                ? request.getCanceledAt()
                : application.getCanceledAt();
        return new AdminHelperActivityResponseDto(application.getId(), request.getId(), request.getStartAt(),
                request.getStudent().getAccountId(), request.getStudent().getName(), status,
                application.getCancelReason(), application.getCancelReasonDetail(), canceledAt,
                helperCanceled ? minutesBetween(application.getCanceledAt(), request.getStartAt()) : null,
                status == ApplicationStatus.NO_SHOW
                        ? minutesBetween(request.getCompletedAt(), request.getNoShowReportedAt())
                        : null,
                application.getAfterAction(),
                helperCanceled ? promotedWaitingOrder(application, promotions) : null,
                request.getStatus());
    }

    // 도우미 취소와 다음 예비 승격은 한 트랜잭션에서 같은 시각으로 기록된다 — 취소 시각에 승격된 지원이 뒤를 이은 예비다
    private static Integer promotedWaitingOrder(Application canceled, List<Application> promotions) {
        if (canceled.getAfterAction() != ApplicationAfterAction.PROMOTED) {
            return null;
        }
        return promotions.stream()
                .filter(application -> Objects.equals(application.getPromotedAt(), canceled.getCanceledAt()))
                .findFirst()
                .map(Application::getPromotedWaitingOrder)
                .orElse(null);
    }

    private static Long minutesBetween(LocalDateTime from, LocalDateTime to) {
        return from == null || to == null ? null : Duration.between(from, to).toMinutes();
    }
}
