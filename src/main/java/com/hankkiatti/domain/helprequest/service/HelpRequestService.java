package com.hankkiatti.domain.helprequest.service;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.dto.request.HelpRequestCreateRequestDto;
import com.hankkiatti.domain.helprequest.dto.response.HelpRequestCreateResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.MatchedHelperResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.MyHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.MyHelpRequestsResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.event.HelpRequestCanceledByStudentEvent;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 장애학생의 도우미 신청 (기능명세서 "도우미 신청").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelpRequestService {

    private final HelpRequestRepository helpRequestRepository;
    private final StudentRepository studentRepository;
    private final ApplicationRepository applicationRepository;
    private final HelpRequestSchedule helpRequestSchedule;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 신청을 만든다. 지금 고를 수 있는 시작 시각인지, 내 진행 중인 신청(모집 중·매칭 완료)과 시간이 겹치지 않는지 확인한다.
     * 겹치면 막는다 — 한 사람이 겹치는 시간에 도우미 둘을 쓸 수 없다 (2026-10-08 결정, 명세는 "경고").
     */
    @Transactional
    public HelpRequestCreateResponseDto create(Long accountId, HelpRequestCreateRequestDto request) {
        LocalDateTime startAt = request.startAt();
        if (!helpRequestSchedule.isBookable(startAt, LocalDateTime.now(clock))) {
            throw new HelpRequestException(HelpRequestErrorType.START_TIME_NOT_AVAILABLE, "startAt=" + startAt);
        }

        // 장애학생 행 잠금을 트랜잭션의 첫 쿼리로 둔다. 같은 장애학생이 동시에 보낸 신청이 한 줄로 서고,
        // MySQL(REPEATABLE READ)에서도 먼저 커밋된 신청까지 보고 겹침을 판단한다
        Student student = studentRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorType.ACCESS_DENIED, "장애학생 전용, accountId=" + accountId));

        LocalDateTime endAt = startAt.plusHours(HelpRequest.USAGE_HOURS);
        if (helpRequestRepository.existsOverlapping(accountId, startAt, endAt)) {
            throw new HelpRequestException(HelpRequestErrorType.TIME_OVERLAP,
                    "accountId=" + accountId + ", startAt=" + startAt);
        }

        // 기타를 고르지 않았으면 기타 내용은 저장하지 않는다
        String otherHelpText = request.helpTypes().contains(HelpType.OTHER) ? trimToNull(request.otherHelpText()) : null;
        HelpRequest helpRequest = helpRequestRepository.save(new HelpRequest(
                student, startAt, request.helpTypes(), otherHelpText, trimToNull(request.memo())));

        // 메모·기타 내용에는 장애 관련 내용이 들어갈 수 있어 로그에 남기지 않는다
        log.info("도우미 신청: helpRequestId={}, studentId={}", helpRequest.getId(), accountId);
        return new HelpRequestCreateResponseDto(helpRequest.getId(), helpRequest.getStartAt(), helpRequest.getEndAt(),
                helpRequest.getHelpTypes().stream().sorted().toList(), helpRequest.getOtherHelpText(),
                helpRequest.getMemo(), helpRequest.getStatus());
    }

    /**
     * 내 신청 (F-02 홈). 진행 중(모집 중·매칭 완료)이면 다가오는 신청, 끝났으면 지난 신청이다 (Figma "F-02 내 신청 · 목록").
     * 매칭된 도우미는 이름·카톡 ID만 넣고, 예비 명단·도우미 전화번호는 넣지 않는다.
     */
    @Transactional(readOnly = true)
    public MyHelpRequestsResponseDto getMyRequests(Long accountId) {
        requireStudent(accountId);
        List<HelpRequest> requests = helpRequestRepository.findByStudentAccountId(accountId);
        Map<Long, Helper> helpers = matchedHelpers(requests);
        LocalDateTime now = LocalDateTime.now(clock);

        Comparator<HelpRequest> byStartAt = Comparator.comparing(HelpRequest::getStartAt)
                .thenComparing(HelpRequest::getId);
        List<MyHelpRequestResponseDto> upcoming = requests.stream()
                .filter(request -> request.getStatus().isInProgress())
                .sorted(byStartAt)
                .map(request -> toMyRequest(request, helpers.get(request.getId()), now))
                .toList();
        List<MyHelpRequestResponseDto> past = requests.stream()
                .filter(request -> !request.getStatus().isInProgress())
                .sorted(byStartAt.reversed())
                .map(request -> toMyRequest(request, helpers.get(request.getId()), now))
                .toList();
        return new MyHelpRequestsResponseDto(upcoming, past);
    }

    /**
     * 모집 중인 내 신청을 바로 철회한다 (기능명세서 "신청 철회", 확인 단계 없음). 식사가 시작된 신청은 철회할 수 없다.
     * 신청 행을 잠가, 같은 신청에 동시에 들어온 철회·지원 가운데 먼저 잡은 쪽만 성공한다.
     */
    @Transactional
    public MyHelpRequestResponseDto withdraw(Long accountId, Long helpRequestId) {
        HelpRequest request = findMyRequestForUpdate(accountId, helpRequestId);

        LocalDateTime now = LocalDateTime.now(clock);
        request.withdraw(now);
        log.info("신청 철회: helpRequestId={}, studentId={}", helpRequestId, accountId);
        // 모집 중이던 신청이라 매칭된 도우미가 없다
        return toMyRequest(request, null, now);
    }

    /**
     * 이용 완료 후 24시간 안에 "도우미가 오지 않았어요"를 신고한다 (기능명세서 "노쇼 신고"). 신청과 지원 모두 노쇼가 되고
     * 도우미 봉사시간은 0이 된다. 센터 승인 없이 바로 바뀌고, 센터는 취소·노쇼 이력을 보고 사후에 판단한다.
     */
    @Transactional
    public MyHelpRequestResponseDto reportNoShow(Long accountId, Long helpRequestId) {
        HelpRequest request = findMyRequestForUpdate(accountId, helpRequestId);

        LocalDateTime now = LocalDateTime.now(clock);
        request.reportNoShow(now);
        List<Application> completed =
                applicationRepository.findByHelpRequestIdAndStatus(helpRequestId, ApplicationStatus.COMPLETED);
        if (completed.isEmpty()) {
            log.warn("이용 완료 지원이 없는 신청을 노쇼로 처리: helpRequestId={}", helpRequestId);
        }
        completed.forEach(Application::markNoShow);

        log.info("노쇼 신고: helpRequestId={}, studentId={}", helpRequestId, accountId);
        Helper helper = completed.isEmpty() ? null : completed.get(0).getHelper();
        return toMyRequest(request, helper, now);
    }

    /**
     * 매칭된 신청을 바로 취소한다 (기능명세서 "매칭 취소" — 장애학생, 사유·패널티 없음). 매칭·승격 응답 대기·예비 도우미의 지원은
     * 모두 학생 사정 취소가 되고, 도우미들에게 알림을 보낼 수 있게 이벤트를 발행한다. 식사가 시작된 뒤에는 막는다.
     * 락 순서: 신청 → 지원. 도우미 취소·지원·승격 응답과 신청 행에서 한 줄로 선다.
     */
    @Transactional
    public MyHelpRequestResponseDto cancelMatched(Long accountId, Long helpRequestId) {
        HelpRequest request = findMyRequestForUpdate(accountId, helpRequestId);

        LocalDateTime now = LocalDateTime.now(clock);
        request.cancelByStudent(now);
        List<Application> active = applicationRepository.findActiveForUpdate(helpRequestId);
        active.forEach(Application::cancelByStudent);

        List<Long> applicationIds = active.stream().map(Application::getId).toList();
        eventPublisher.publishEvent(
                new HelpRequestCanceledByStudentEvent(helpRequestId, request.getStartAt(), applicationIds));
        log.info("장애학생 매칭 취소: helpRequestId={}, studentId={}, 학생 취소된 지원={}건",
                helpRequestId, accountId, active.size());
        // 취소된 신청이라 도우미 정보는 넣지 않는다
        return toMyRequest(request, null, now);
    }

    // 장애학생 본인의 신청을 신청 행 락으로 가져온다. 없는 신청과 남의 신청은 같은 404로 응답한다
    private HelpRequest findMyRequestForUpdate(Long accountId, Long helpRequestId) {
        requireStudent(accountId);
        return helpRequestRepository.findByIdForUpdate(helpRequestId)
                .filter(found -> found.isRequestedBy(accountId))
                .orElseThrow(() -> new HelpRequestException(HelpRequestErrorType.NOT_FOUND,
                        "helpRequestId=" + helpRequestId + ", accountId=" + accountId));
    }

    private void requireStudent(Long accountId) {
        if (!studentRepository.existsById(accountId)) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED, "장애학생 전용, accountId=" + accountId);
        }
    }

    // 신청 ID → 매칭된 도우미. 한 신청에 매칭된 지원이 여럿이면 가장 최근 지원의 도우미
    private Map<Long, Helper> matchedHelpers(List<HelpRequest> requests) {
        if (requests.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = requests.stream().map(HelpRequest::getId).toList();
        return applicationRepository.findMatchedWithHelper(ids).stream()
                .sorted(Comparator.comparing(Application::getId))
                .collect(Collectors.toMap(application -> application.getHelpRequest().getId(), Application::getHelper,
                        (older, newer) -> newer));
    }

    private MyHelpRequestResponseDto toMyRequest(HelpRequest request, Helper helper, LocalDateTime now) {
        boolean noShowReportable = request.canReportNoShow(now);
        return new MyHelpRequestResponseDto(request.getId(), request.getStartAt(), request.getEndAt(),
                request.getStatus(), request.getHelpTypes().stream().sorted().toList(), request.getOtherHelpText(),
                request.getMemo(),
                helper == null ? null : new MatchedHelperResponseDto(helper.getName(), helper.getKakaoId()),
                request.isHelperChanged(), noShowReportable,
                noShowReportable ? request.noShowReportDeadline() : null);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
