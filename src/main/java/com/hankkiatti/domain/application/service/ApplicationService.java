package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.application.dto.response.ApplyResponseDto;
import com.hankkiatti.domain.application.dto.response.ApplyStudentResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.ApplyBlockReason;
import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.event.WaitingRegisteredEvent;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도우미 지원 (기능명세서 "요청 목록 · 지원", 요구사항 4.2). 모집 중인 신청에 처음 지원하면 바로 매칭, 이미 매칭된 신청이면 예비 N번(최대 3명).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final HelpRequestRepository helpRequestRepository;
    private final HelperRepository helperRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplyPolicy applyPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 락 순서: 신청 → 도우미. 같은 신청에 동시에 들어온 지원은 신청 락에서, 같은 도우미의 겹치는 지원·관리자 비활성화는 도우미 락에서 한 줄로 선다.
     * 두 락을 트랜잭션의 첫 쿼리로 둬서 MySQL(REPEATABLE READ)에서도 먼저 커밋된 지원까지 보고 판단한다.
     */
    @Transactional
    public ApplyResponseDto apply(Long helperId, Long helpRequestId) {
        HelpRequest request = helpRequestRepository.findByIdForUpdate(helpRequestId)
                .orElseThrow(() -> new HelpRequestException(HelpRequestErrorType.NOT_FOUND,
                        "helpRequestId=" + helpRequestId));
        Helper helper = helperRepository.findByIdForUpdate(helperId)
                .orElseThrow(() -> new AuthException(AuthErrorType.ACCESS_DENIED, "도우미 전용, accountId=" + helperId));
        // 관리자 비활성화가 도우미 행을 잠근 뒤 커밋하므로, 그보다 늦게 잠근 지원은 여기서 막힌다 (해제 대상에서 빠지는 지원이 없게)
        if (!helper.getAccount().isActive()) {
            throw new AuthException(AuthErrorType.ACCOUNT_DEACTIVATED, "helperId=" + helperId);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (!request.getStatus().isInProgress() || !now.isBefore(request.getStartAt())) {
            throw new ApplicationException(ApplicationErrorType.NOT_OPEN,
                    "helpRequestId=" + helpRequestId + ", status=" + request.getStatus());
        }

        List<Application> myActive = applicationRepository.findActiveWithHelpRequestByHelperId(helperId);
        // 신청 행을 잠근 뒤 세므로 같은 신청의 동시 지원이 상한을 넘겨 예비를 만들지 않는다. 모집 중이면 예비가 없어 세지 않는다
        long waitingCount = request.getStatus() == HelpRequestStatus.RECRUITING ? 0
                : applicationRepository.countByHelpRequestIdAndStatus(helpRequestId, ApplicationStatus.WAITING);
        ApplyBlockReason blockReason = applyPolicy.blockReason(request, myActive, waitingCount);
        if (blockReason == ApplyBlockReason.ALREADY_APPLIED) {
            throw new ApplicationException(ApplicationErrorType.ALREADY_APPLIED,
                    "helpRequestId=" + helpRequestId + ", helperId=" + helperId);
        }
        if (blockReason == ApplyBlockReason.TIME_OVERLAP) {
            throw new ApplicationException(ApplicationErrorType.TIME_OVERLAP,
                    "helpRequestId=" + helpRequestId + ", helperId=" + helperId);
        }
        if (blockReason == ApplyBlockReason.WAITING_FULL) {
            throw new ApplicationException(ApplicationErrorType.WAITING_FULL,
                    "helpRequestId=" + helpRequestId + ", waitingCount=" + waitingCount);
        }

        Application application = new Application(request, helper, now);
        if (request.getStatus() == HelpRequestStatus.RECRUITING) {
            application.match(now);
            request.match(now);
            applicationRepository.save(application);
            log.info("지원 → 바로 매칭: applicationId={}, helpRequestId={}, helperId={}",
                    application.getId(), helpRequestId, helperId);
            // 커밋 뒤 이 도우미의 겹치는 다른 예비를 자동 제외하고(BE-32), 장애학생·도우미에게 매칭 알림을 쌓는다(BE-51)
            eventPublisher.publishEvent(new HelperConfirmedEvent(helperId, helpRequestId, application.getId(),
                    request.getStartAt(), request.getEndAt(), HelperConfirmedEvent.Kind.DIRECT_MATCH));
            Student student = request.getStudent();
            return toResponse(application, request, null,
                    new ApplyStudentResponseDto(student.getName(), student.getKakaoId()));
        }

        // 매칭 완료인 신청 — 지원 순서대로 예비. 상한(ApplyPolicy.MAX_WAITING)은 위에서 막았다
        int waitingOrder = (int) waitingCount + 1;
        applicationRepository.save(application);
        log.info("지원 → 예비 {}번: applicationId={}, helpRequestId={}, helperId={}",
                waitingOrder, application.getId(), helpRequestId, helperId);
        eventPublisher.publishEvent(new WaitingRegisteredEvent(application.getId(), waitingOrder));
        return toResponse(application, request, waitingOrder, null);
    }

    private ApplyResponseDto toResponse(Application application, HelpRequest request, Integer waitingOrder,
                                        ApplyStudentResponseDto student) {
        return new ApplyResponseDto(application.getId(), request.getId(), application.getStatus(),
                request.getStartAt(), request.getEndAt(), request.getHelpTypes().stream().sorted().toList(),
                waitingOrder, student);
    }
}
