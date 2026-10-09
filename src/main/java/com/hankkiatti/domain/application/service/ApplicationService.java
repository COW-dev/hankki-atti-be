package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.application.dto.response.ApplyResponseDto;
import com.hankkiatti.domain.application.dto.response.ApplyStudentResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.ApplyBlockReason;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도우미 지원 (기능명세서 "요청 목록 · 지원", 요구사항 4.2). 모집 중인 신청에 처음 지원하면 바로 매칭, 이미 매칭된 신청이면 예비 N번.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final HelpRequestRepository helpRequestRepository;
    private final HelperRepository helperRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplyPolicy applyPolicy;
    private final Clock clock;

    /**
     * 락 순서: 신청 → 도우미. 같은 신청에 동시에 들어온 지원은 신청 락에서, 같은 도우미의 겹치는 지원은 도우미 락에서 한 줄로 선다.
     * 두 락을 트랜잭션의 첫 쿼리로 둬서 MySQL(REPEATABLE READ)에서도 먼저 커밋된 지원까지 보고 판단한다.
     */
    @Transactional
    public ApplyResponseDto apply(Long helperId, Long helpRequestId) {
        HelpRequest request = helpRequestRepository.findByIdForUpdate(helpRequestId)
                .orElseThrow(() -> new HelpRequestException(HelpRequestErrorType.NOT_FOUND,
                        "helpRequestId=" + helpRequestId));
        Helper helper = helperRepository.findByIdForUpdate(helperId)
                .orElseThrow(() -> new AuthException(AuthErrorType.ACCESS_DENIED, "도우미 전용, accountId=" + helperId));

        LocalDateTime now = LocalDateTime.now(clock);
        if (!request.getStatus().isInProgress() || !now.isBefore(request.getStartAt())) {
            throw new ApplicationException(ApplicationErrorType.NOT_OPEN,
                    "helpRequestId=" + helpRequestId + ", status=" + request.getStatus());
        }

        List<Application> myActive = applicationRepository.findActiveWithHelpRequestByHelperId(helperId);
        ApplyBlockReason blockReason = applyPolicy.blockReason(request, myActive);
        if (blockReason == ApplyBlockReason.ALREADY_APPLIED) {
            throw new ApplicationException(ApplicationErrorType.ALREADY_APPLIED,
                    "helpRequestId=" + helpRequestId + ", helperId=" + helperId);
        }
        if (blockReason == ApplyBlockReason.TIME_OVERLAP) {
            throw new ApplicationException(ApplicationErrorType.TIME_OVERLAP,
                    "helpRequestId=" + helpRequestId + ", helperId=" + helperId);
        }

        Application application = new Application(request, helper, now);
        if (request.getStatus() == HelpRequestStatus.RECRUITING) {
            application.match(now);
            request.match(now);
            applicationRepository.save(application);
            log.info("지원 → 바로 매칭: applicationId={}, helpRequestId={}, helperId={}",
                    application.getId(), helpRequestId, helperId);
            Student student = request.getStudent();
            return toResponse(application, request, null,
                    new ApplyStudentResponseDto(student.getName(), student.getKakaoId()));
        }

        // 매칭 완료인 신청 — 지원 순서대로 예비. 상한은 없다 (PM 미정, 2026-10-09)
        int waitingOrder = (int) applicationRepository.countByHelpRequestIdAndStatus(
                helpRequestId, ApplicationStatus.WAITING) + 1;
        applicationRepository.save(application);
        log.info("지원 → 예비 {}번: applicationId={}, helpRequestId={}, helperId={}",
                waitingOrder, application.getId(), helpRequestId, helperId);
        return toResponse(application, request, waitingOrder, null);
    }

    private ApplyResponseDto toResponse(Application application, HelpRequest request, Integer waitingOrder,
                                        ApplyStudentResponseDto student) {
        return new ApplyResponseDto(application.getId(), request.getId(), application.getStatus(),
                request.getStartAt(), request.getEndAt(), request.getHelpTypes().stream().sorted().toList(),
                waitingOrder, student);
    }
}
