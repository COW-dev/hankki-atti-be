package com.hankkiatti.domain.helprequest.service;

import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helprequest.dto.request.HelpRequestCreateRequestDto;
import com.hankkiatti.domain.helprequest.dto.response.HelpRequestCreateResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final HelpRequestSchedule helpRequestSchedule;
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

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
