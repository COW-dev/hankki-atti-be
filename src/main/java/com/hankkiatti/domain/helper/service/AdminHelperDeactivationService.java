package com.hankkiatti.domain.helper.service;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.HelperDeactivationCanceler;
import com.hankkiatti.domain.helper.dto.response.AdminHelperAccountStatusResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDeactivationPreviewResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDeactivationResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDeactivationTargetResponseDto;
import com.hankkiatti.domain.helper.exception.HelperErrorType;
import com.hankkiatti.domain.helper.exception.HelperException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 도우미 비활성화·미리보기·재활성화 (Notion 기능명세서 "도우미 계정 비활성화", Figma AF-03 확인 모달). 전체·제한 권한 모두.
 * 진행 중 매칭은 도우미 취소처럼(사유 관리자 비활성화, 패널티 이력 아님) 예비 승격 / 모집 재개하고, 예비 자리는 빠진다.
 * 식사가 이미 시작된 매칭은 건드리지 않는다 — 도우미 취소도 식사 시작 뒤에는 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminHelperDeactivationService {

    // 비활성화 직전에 시작된 지원이 계정 커밋 뒤에 끝났을 수 있어 대상을 한 번 더 모은다
    private static final int RELEASE_ROUNDS = 2;

    private final HelperAccountService helperAccountService;
    private final HelperDeactivationCanceler helperDeactivationCanceler;
    private final ApplicationRepository applicationRepository;
    private final Clock clock;

    /**
     * 비활성화하면 해제될 매칭·예비 (확인 모달). 이미 비활성이면 그때 다 정리되지 못하고 남은 건이다.
     */
    @Transactional(readOnly = true)
    public AdminHelperDeactivationPreviewResponseDto preview(Long helperId) {
        Account account = helperAccountService.getAccount(helperId);
        LocalDateTime now = LocalDateTime.now(clock);
        List<Application> targets = applicationRepository.findMineWithHelpRequest(helperId).stream()
                .filter(Application::isActive)
                .filter(application -> now.isBefore(application.getHelpRequest().getStartAt()))
                .sorted(Comparator.comparing((Application application) -> application.getHelpRequest().getStartAt())
                        .thenComparing(Application::getId))
                .toList();
        Map<Long, Integer> waitingOrders = waitingOrders(targets);

        List<AdminHelperDeactivationTargetResponseDto> rows = targets.stream()
                .map(application -> new AdminHelperDeactivationTargetResponseDto(application.getId(),
                        application.getHelpRequest().getId(), application.getHelpRequest().getStartAt(),
                        application.getHelpRequest().getStudent().getName(), application.getStatus(),
                        waitingOrders.get(application.getId())))
                .toList();
        long waiting = targets.stream().filter(application -> application.getStatus() == ApplicationStatus.WAITING)
                .count();
        return new AdminHelperDeactivationPreviewResponseDto(helperId, !account.isActive(),
                targets.size() - waiting, waiting, rows);
    }

    /**
     * 계정을 먼저 비활성화(커밋)하고, 남은 매칭·예비를 지원마다 새 트랜잭션에서 해제한다. 한 건이 실패해도 나머지는 계속하고,
     * 같은 요청을 다시 보내면 남은 건만 처리한다 (계정은 이미 비활성). 이미 비활성인데 남은 것도 없으면 409.
     * 트랜잭션을 걸지 않는다 — 계정과 지원마다의 트랜잭션이 각자 커밋돼야 한다.
     */
    public AdminHelperDeactivationResponseDto deactivate(Long adminAccountId, Long helperId) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean newlyDeactivated = helperAccountService.deactivate(helperId, now);

        int releasedMatches = 0;
        int releasedWaits = 0;
        for (int round = 0; round < RELEASE_ROUNDS; round++) {
            List<Long> applicationIds = applicationRepository.findActiveIdsBeforeMeal(helperId, now);
            if (applicationIds.isEmpty()) {
                break;
            }
            for (Long applicationId : applicationIds) {
                Optional<ApplicationStatus> released = releaseQuietly(applicationId, now);
                if (released.isEmpty()) {
                    continue;
                }
                if (released.get() == ApplicationStatus.WAITING) {
                    releasedWaits++;
                } else {
                    releasedMatches++;
                }
            }
        }
        int remaining = applicationRepository.findActiveIdsBeforeMeal(helperId, now).size();
        if (!newlyDeactivated && releasedMatches == 0 && releasedWaits == 0 && remaining == 0) {
            throw new HelperException(HelperErrorType.ALREADY_INACTIVE, "helperId=" + helperId);
        }

        Account account = helperAccountService.getAccount(helperId);
        log.info("도우미 비활성화: helperId={}, adminAccountId={}, newly={}, matches={}, waits={}, remaining={}",
                helperId, adminAccountId, newlyDeactivated, releasedMatches, releasedWaits, remaining);
        return new AdminHelperDeactivationResponseDto(helperId, account.getStatus(), account.getDeactivatedAt(),
                releasedMatches, releasedWaits, remaining);
    }

    public AdminHelperAccountStatusResponseDto activate(Long adminAccountId, Long helperId) {
        Account account = helperAccountService.activate(helperId);
        log.info("도우미 재활성화: helperId={}, adminAccountId={}", helperId, adminAccountId);
        return new AdminHelperAccountStatusResponseDto(helperId, account.getStatus());
    }

    private Optional<ApplicationStatus> releaseQuietly(Long applicationId, LocalDateTime now) {
        try {
            return helperDeactivationCanceler.release(applicationId, now);
        } catch (RuntimeException e) {
            // 남은 건은 같은 요청을 다시 보내면 처리된다
            log.error("관리자 비활성화 지원 해제 실패: applicationId={}", applicationId, e);
            return Optional.empty();
        }
    }

    // 예비 순번: 신청마다 지금 예비를 지원 순으로 센다 (승격 후보 순서와 같다)
    private Map<Long, Integer> waitingOrders(List<Application> targets) {
        List<Long> requestIds = targets.stream()
                .filter(application -> application.getStatus() == ApplicationStatus.WAITING)
                .map(application -> application.getHelpRequest().getId())
                .distinct()
                .toList();
        Map<Long, Integer> orders = new HashMap<>();
        if (requestIds.isEmpty()) {
            return orders;
        }
        Map<Long, Integer> positions = new HashMap<>();
        for (Application waiting : applicationRepository.findWaitingIn(requestIds)) {
            int order = positions.merge(waiting.getHelpRequest().getId(), 1, Integer::sum);
            orders.put(waiting.getId(), order);
        }
        return orders;
    }
}
