package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.application.dto.response.ApplyStudentResponseDto;
import com.hankkiatti.domain.application.dto.response.MyApplicationResponseDto;
import com.hankkiatti.domain.application.dto.response.MyApplicationsResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.MyApplicationFilter;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.student.entity.Student;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyApplicationService {

    private static final Comparator<Application> BY_START_AT =
            Comparator.comparing((Application application) -> application.getHelpRequest().getStartAt())
                    .thenComparing(Application::getId);

    private final ApplicationRepository applicationRepository;
    private final HelperRepository helperRepository;

    /**
     * 매칭 현황 (F-07 도우미 홈). 진행 중(매칭 완료·승격 응답 대기·예비)과 지난 활동으로 나눈다.
     * 장애학생 이름·카톡 ID는 매칭 완료 카드에만 넣는다 — 예비·승격 응답 대기·지난 활동은 블라인드다.
     * 빠짐·승격 거절도 본인 활동 기록이라 지난 활동에 그대로 넣는다 (요구사항 "논의 필요", 2026-10-10 결정).
     */
    @Transactional(readOnly = true)
    public MyApplicationsResponseDto getMyApplications(Long helperId, MyApplicationFilter filter) {
        requireHelper(helperId);
        List<Application> mine = applicationRepository.findMineWithHelpRequest(helperId).stream()
                .filter(application -> filter.includes(application.getStatus()))
                .toList();
        Map<Long, Integer> waitingOrders = waitingOrders(mine);

        List<MyApplicationResponseDto> inProgress = mine.stream()
                .filter(Application::isActive)
                .sorted(BY_START_AT)
                .map(application -> toResponse(application, waitingOrders.get(application.getId())))
                .toList();
        List<MyApplicationResponseDto> past = mine.stream()
                .filter(application -> !application.isActive())
                .sorted(BY_START_AT.reversed())
                .map(application -> toResponse(application, null))
                .toList();
        return new MyApplicationsResponseDto(inProgress, past);
    }

    private void requireHelper(Long accountId) {
        if (!helperRepository.existsById(accountId)) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED, "도우미 전용, accountId=" + accountId);
        }
    }

    // 내 예비 지원 ID → 그 신청의 예비 중 지금 몇 번째인지 (지원 순, 승격 후보 순서와 같다)
    private Map<Long, Integer> waitingOrders(List<Application> mine) {
        List<Long> helpRequestIds = mine.stream()
                .filter(application -> application.getStatus() == ApplicationStatus.WAITING)
                .map(application -> application.getHelpRequest().getId())
                .toList();
        if (helpRequestIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> nextOrder = new HashMap<>();
        Map<Long, Integer> orders = new HashMap<>();
        for (Application waiting : applicationRepository.findWaitingIn(helpRequestIds)) {
            int order = nextOrder.merge(waiting.getHelpRequest().getId(), 1, Integer::sum);
            orders.put(waiting.getId(), order);
        }
        return orders;
    }

    private MyApplicationResponseDto toResponse(Application application, Integer waitingOrder) {
        HelpRequest request = application.getHelpRequest();
        ApplicationStatus status = application.getStatus();
        ApplyStudentResponseDto student = null;
        if (status == ApplicationStatus.MATCHED) {
            Student matched = request.getStudent();
            student = new ApplyStudentResponseDto(matched.getName(), matched.getKakaoId());
        }
        return new MyApplicationResponseDto(application.getId(), request.getId(), status,
                request.getStartAt(), request.getEndAt(), request.getHelpTypes().stream().sorted().toList(),
                waitingOrder, student,
                status == ApplicationStatus.HELPER_CANCELED ? application.getCancelReason() : null,
                application.getVolunteerHours());
    }
}
