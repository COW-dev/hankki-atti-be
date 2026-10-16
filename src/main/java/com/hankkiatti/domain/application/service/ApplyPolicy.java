package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplyBlockReason;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 도우미가 신청에 지원할 수 있는지. 요청 목록 카드(지원 결과 예상)와 실제 지원 검증이 같은 규칙을 쓰도록 한 곳에 둔다.
 * <ul>
 *   <li>같은 신청에 진행 중 지원(매칭·승격 응답 대기·예비)이 있으면 재지원 불가 (요구사항 4.2)</li>
 *   <li>확정 매칭(매칭 완료·승격 응답 대기)과 이용 시간이 겹치면 지원 불가 (4.3). 예비끼리 겹치는 건 막지 않는다</li>
 *   <li>매칭된 신청의 예비가 {@link #MAX_WAITING}명이면 지원 불가. 모집 중이면 상한과 무관하게 바로 매칭</li>
 * </ul>
 */
@Component
public class ApplyPolicy {

    // 신청 한 건의 예비 인원 상한 (#53, PM 결정). 승격 응답 대기는 세지 않는다
    public static final int MAX_WAITING = 3;

    /**
     * 이유가 여럿이면 도우미 본인 사유를 먼저 준다 — 예비 자리가 나도 재지원·시간 겹침은 풀리지 않으므로.
     *
     * @param helperActive 도우미의 진행 중 지원 (신청까지 읽을 수 있어야 한다)
     * @param waitingCount 이 신청의 지금 예비 인원
     * @return 지원할 수 없는 이유. 지원할 수 있으면 null
     */
    public ApplyBlockReason blockReason(HelpRequest request, List<Application> helperActive, long waitingCount) {
        boolean alreadyApplied = helperActive.stream()
                .anyMatch(application -> application.getHelpRequest().getId().equals(request.getId()));
        if (alreadyApplied) {
            return ApplyBlockReason.ALREADY_APPLIED;
        }
        if (overlapsConfirmed(request, helperActive)) {
            return ApplyBlockReason.TIME_OVERLAP;
        }
        return request.getStatus() == HelpRequestStatus.MATCHED && waitingCount >= MAX_WAITING
                ? ApplyBlockReason.WAITING_FULL : null;
    }

    /**
     * 도우미의 다른 신청 확정 매칭(매칭 완료·승격 응답 대기)과 이 신청의 이용 시간이 겹치는지. 같은 신청의 지원은 보지 않는다.
     * 지원 검증과 예비 승격 후보 확인(BE-34)이 같이 쓴다.
     */
    public boolean overlapsConfirmed(HelpRequest request, List<Application> helperActive) {
        return helperActive.stream()
                .filter(application -> !application.getHelpRequest().getId().equals(request.getId()))
                .filter(application -> application.getStatus().isConfirmed())
                .anyMatch(application -> application.getHelpRequest().overlaps(request.getStartAt(), request.getEndAt()));
    }
}
