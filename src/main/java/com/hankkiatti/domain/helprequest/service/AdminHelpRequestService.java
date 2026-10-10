package com.hankkiatti.domain.helprequest.service;

import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.repository.HelpRequestApplicationCount;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestHelperResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestRow;
import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestStudentResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestSummaryResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.LimitedAdminHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.LimitedAdminHelpRequestStudentResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관리자 전체 신청 현황 (Notion 기능명세서 "전체 신청 현황", AF-02). 조회만 — 관리자는 매칭을 바꾸지 않는다.
 * 전체 권한·제한 권한 모두 볼 수 있고, 제한 권한 응답에는 장애 유형을 넣지 않는다 (별도 DTO).
 */
@Service
@RequiredArgsConstructor
public class AdminHelpRequestService {

    private final HelpRequestRepository helpRequestRepository;
    private final ApplicationRepository applicationRepository;
    private final AdminRepository adminRepository;
    private final Clock clock;

    /**
     * 기간(식사 날짜, 기본 오늘~7일 뒤) 안의 신청을 식사 일시 오름차순으로. 상태·검색어(장애학생 이름·학번)는 있으면 거른다.
     * 신청 수와 상관없이 쿼리 4번: 목록, 예비 인원, 확정 도우미, 응답 대기.
     */
    @Transactional(readOnly = true)
    public List<AdminHelpRequestRow> getHelpRequests(Long adminAccountId, LocalDate from, LocalDate to,
                                                     HelpRequestStatus status, String q) {
        Admin admin = requireAdmin(adminAccountId);
        HelpRequestDateRange range = HelpRequestDateRange.of(from, to, LocalDate.now(clock));
        String pattern = StringUtils.hasText(q) ? "%" + q.trim() + "%" : null;
        List<HelpRequest> requests = helpRequestRepository.findForAdmin(
                range.startInclusive(), range.endExclusive(), status, pattern);
        if (requests.isEmpty()) {
            return List.of();
        }

        List<Long> ids = requests.stream().map(HelpRequest::getId).toList();
        Map<Long, Long> waitingCounts = applicationRepository.countWaitingByHelpRequest(ids).stream()
                .collect(Collectors.toMap(HelpRequestApplicationCount::helpRequestId, HelpRequestApplicationCount::count));
        Map<Long, Helper> confirmedHelpers = confirmedHelpers(ids);
        Set<Long> awaitingPromotion = new HashSet<>(applicationRepository.findHelpRequestIdsAwaitingPromotion(ids));

        return requests.stream()
                .map(request -> toRow(admin, request, confirmedHelpers.get(request.getId()),
                        awaitingPromotion.contains(request.getId()), waitingCounts.getOrDefault(request.getId(), 0L)))
                .toList();
    }

    /**
     * 요약 숫자 (선택 기간 기준, 상태·검색과 상관없이). 신청 = 기간 안 모든 신청(철회·취소 포함),
     * 매칭 완료 = 도우미가 확정된 신청(매칭 완료 + 이용 완료) — 2026-10-10 결정, Figma 예시와 같은 기준.
     */
    @Transactional(readOnly = true)
    public AdminHelpRequestSummaryResponseDto getSummary(Long adminAccountId, LocalDate from, LocalDate to) {
        requireAdmin(adminAccountId);
        HelpRequestDateRange range = HelpRequestDateRange.of(from, to, LocalDate.now(clock));
        Map<HelpRequestStatus, Long> counts = new EnumMap<>(HelpRequestStatus.class);
        helpRequestRepository.countByStatusBetween(range.startInclusive(), range.endExclusive())
                .forEach(count -> counts.put(count.status(), count.count()));

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new AdminHelpRequestSummaryResponseDto(total,
                counts.getOrDefault(HelpRequestStatus.MATCHED, 0L) + counts.getOrDefault(HelpRequestStatus.COMPLETED, 0L),
                counts.getOrDefault(HelpRequestStatus.RECRUITING, 0L),
                counts.getOrDefault(HelpRequestStatus.FAILED, 0L),
                counts.getOrDefault(HelpRequestStatus.NO_SHOW, 0L));
    }

    // /api/admin/** 경로는 관리자만 들어오지만, 등급을 보려면 관리자 프로필이 필요하다
    private Admin requireAdmin(Long adminAccountId) {
        return adminRepository.findById(adminAccountId)
                .orElseThrow(() -> new AuthException(AuthErrorType.ACCESS_DENIED, "adminAccountId=" + adminAccountId));
    }

    // 신청 ID → 확정된 도우미(매칭 완료·이용 완료·노쇼). 한 신청에 여럿이면 가장 최근 지원의 도우미
    private Map<Long, Helper> confirmedHelpers(List<Long> ids) {
        return applicationRepository.findMatchedWithHelper(ids).stream()
                .sorted(Comparator.comparing(Application::getId))
                .collect(Collectors.toMap(application -> application.getHelpRequest().getId(), Application::getHelper,
                        (older, newer) -> newer));
    }

    private static AdminHelpRequestRow toRow(Admin admin, HelpRequest request, Helper helper,
                                             boolean promotionPending, long waitingCount) {
        Student student = request.getStudent();
        AdminHelpRequestHelperResponseDto helperDto = helper == null
                ? null
                : new AdminHelpRequestHelperResponseDto(helper.getAccountId(), helper.getName());
        LocalDateTime requestedAt = request.getCreatedAt();
        LocalDateTime firstMatchedAt = request.getFirstMatchedAt();
        Long minutesToMatch = firstMatchedAt == null ? null : Duration.between(requestedAt, firstMatchedAt).toMinutes();
        if (admin.isFull()) {
            return new AdminHelpRequestResponseDto(request.getId(), request.getStartAt(), request.getEndAt(),
                    request.getStatus(), request.getHelpTypes().stream().sorted().toList(), request.getCancelType(),
                    new AdminHelpRequestStudentResponseDto(student.getAccountId(), student.getName(),
                            student.getStudentNo(), student.getDisabilityType()),
                    helperDto, promotionPending, request.isHelperChanged(), waitingCount, requestedAt, firstMatchedAt,
                    minutesToMatch);
        }
        return new LimitedAdminHelpRequestResponseDto(request.getId(), request.getStartAt(), request.getEndAt(),
                request.getStatus(), request.getHelpTypes().stream().sorted().toList(), request.getCancelType(),
                new LimitedAdminHelpRequestStudentResponseDto(student.getAccountId(), student.getName(),
                        student.getStudentNo()),
                helperDto, promotionPending, request.isHelperChanged(), waitingCount, requestedAt, firstMatchedAt,
                minutesToMatch);
    }
}
