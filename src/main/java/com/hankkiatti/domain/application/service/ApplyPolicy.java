package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplyBlockReason;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 도우미가 신청에 지원할 수 있는지. 요청 목록 카드(지원 결과 예상)와 실제 지원 검증이 같은 규칙을 쓰도록 한 곳에 둔다.
 * <ul>
 *   <li>같은 신청에 진행 중 지원(매칭·승격 응답 대기·예비)이 있으면 재지원 불가 (요구사항 4.2)</li>
 *   <li>확정 매칭(매칭 완료·승격 응답 대기)과 이용 시간이 겹치면 지원 불가 (4.3). 예비끼리 겹치는 건 막지 않는다</li>
 * </ul>
 */
@Component
public class ApplyPolicy {

    /**
     * @param helperActive 도우미의 진행 중 지원 (신청까지 읽을 수 있어야 한다)
     * @return 지원할 수 없는 이유. 지원할 수 있으면 null
     */
    public ApplyBlockReason blockReason(HelpRequest request, List<Application> helperActive) {
        boolean alreadyApplied = helperActive.stream()
                .anyMatch(application -> application.getHelpRequest().getId().equals(request.getId()));
        if (alreadyApplied) {
            return ApplyBlockReason.ALREADY_APPLIED;
        }
        boolean overlapsConfirmed = helperActive.stream()
                .filter(application -> application.getStatus().isConfirmed())
                .anyMatch(application -> application.getHelpRequest().overlaps(request.getStartAt(), request.getEndAt()));
        return overlapsConfirmed ? ApplyBlockReason.TIME_OVERLAP : null;
    }
}
