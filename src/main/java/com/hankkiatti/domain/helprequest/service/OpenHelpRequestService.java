package com.hankkiatti.domain.helprequest.service;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplyBlockReason;
import com.hankkiatti.domain.application.entity.ApplyOutcome;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplyPolicy;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.dto.response.OpenHelpRequestDateResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.OpenHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도우미의 요청 목록 (F-06). 지원할 수 있는 신청을 날짜별로 묶고, 카드마다 지금 지원하면 어떻게 되는지를 함께 준다.
 * 블라인드 — 장애학생 정보·기타 도움 내용·메모·예비 인원은 넣지 않는다 (요구사항 4.8).
 */
@Service
@RequiredArgsConstructor
public class OpenHelpRequestService {

    // 한 번에 조회할 수 있는 최대 기간 (한 달치)

    private final HelpRequestRepository helpRequestRepository;
    private final ApplicationRepository applicationRepository;
    private final HelperRepository helperRepository;
    private final ApplyPolicy applyPolicy;
    private final Clock clock;

    /**
     * @param from 조회 시작 날짜. 없으면 오늘
     * @param to   조회 끝 날짜(포함). 없으면 from + 7일 (신청이 생길 수 있는 범위 전부)
     */
    @Transactional(readOnly = true)
    public List<OpenHelpRequestDateResponseDto> getOpenRequests(Long accountId, LocalDate from, LocalDate to) {
        requireHelper(accountId);
        LocalDateTime now = LocalDateTime.now(clock);
        HelpRequestDateRange range = HelpRequestDateRange.of(from, to, now.toLocalDate());

        List<HelpRequest> requests = helpRequestRepository.findOpen(range.startInclusive(), range.endExclusive(), now);
        List<Application> myActive = applicationRepository.findActiveWithHelpRequestByHelperId(accountId);

        Map<LocalDate, List<OpenHelpRequestResponseDto>> byDate = requests.stream()
                .collect(Collectors.groupingBy(request -> request.getStartAt().toLocalDate(), LinkedHashMap::new,
                        Collectors.mapping(request -> toCard(request, myActive), Collectors.toList())));
        return byDate.entrySet().stream()
                .map(entry -> new OpenHelpRequestDateResponseDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void requireHelper(Long accountId) {
        if (!helperRepository.existsById(accountId)) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED, "도우미 전용, accountId=" + accountId);
        }
    }

    private OpenHelpRequestResponseDto toCard(HelpRequest request, List<Application> myActive) {
        ApplyBlockReason blockReason = applyPolicy.blockReason(request, myActive);
        ApplyOutcome outcome = blockReason != null ? ApplyOutcome.BLOCKED
                : request.getStatus() == HelpRequestStatus.RECRUITING ? ApplyOutcome.MATCH
                : ApplyOutcome.WAITING;
        return new OpenHelpRequestResponseDto(request.getId(), request.getStartAt(), request.getEndAt(),
                request.getHelpTypes().stream().sorted().toList(), outcome, blockReason);
    }
}
