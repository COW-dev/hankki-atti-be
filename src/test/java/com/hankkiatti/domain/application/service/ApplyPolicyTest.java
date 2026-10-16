package com.hankkiatti.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplyBlockReason;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestHelpRequests;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApplyPolicyTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);
    private static final LocalDateTime APPLIED_AT = NOON.minusDays(1);

    private final ApplyPolicy applyPolicy = new ApplyPolicy();
    private final Student student = TestHelpRequests.student("60231234");
    private final Helper helper = TestHelpRequests.helper("60230001");

    private HelpRequest request(Long id, LocalDateTime startAt) {
        HelpRequest request = TestHelpRequests.request(student, startAt);
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private HelpRequest matchedRequest(Long id, LocalDateTime startAt) {
        HelpRequest request = request(id, startAt);
        request.match(APPLIED_AT);
        return request;
    }

    private Application waiting(HelpRequest request) {
        return new Application(request, helper, APPLIED_AT);
    }

    private Application matched(HelpRequest request) {
        Application application = waiting(request);
        application.match(APPLIED_AT);
        return application;
    }

    @Test
    void blockReason_진행중지원없고겹침없음_null() {
        // when & then
        assertThat(applyPolicy.blockReason(request(1L, NOON), List.of(), 0)).isNull();
    }

    @Test
    void blockReason_같은신청에예비로지원함_ALREADY_APPLIED() {
        // given
        HelpRequest target = request(1L, NOON);

        // when & then
        assertThat(applyPolicy.blockReason(target, List.of(waiting(target)), 0))
                .isEqualTo(ApplyBlockReason.ALREADY_APPLIED);
    }

    @Test
    void blockReason_매칭과30분겹침_TIME_OVERLAP이고_예비와겹침은허용() {
        // given
        HelpRequest target = request(1L, NOON.plusMinutes(30));
        HelpRequest other = request(2L, NOON);

        // when & then
        assertThat(applyPolicy.blockReason(target, List.of(matched(other)), 0)).isEqualTo(ApplyBlockReason.TIME_OVERLAP);
        assertThat(applyPolicy.blockReason(target, List.of(waiting(other)), 0)).isNull();
    }

    @Test
    void blockReason_매칭끝과시작이같음_겹치지않음() {
        // when & then — 12:00~13:00 매칭, 13:00 신청
        assertThat(applyPolicy.blockReason(request(1L, NOON.plusHours(1)), List.of(matched(request(2L, NOON))), 0))
                .isNull();
    }

    @Test
    void blockReason_매칭된신청예비3명_WAITING_FULL이고2명이면null() {
        // given
        HelpRequest target = matchedRequest(1L, NOON);

        // when & then
        assertThat(applyPolicy.blockReason(target, List.of(), ApplyPolicy.MAX_WAITING))
                .isEqualTo(ApplyBlockReason.WAITING_FULL);
        assertThat(applyPolicy.blockReason(target, List.of(), ApplyPolicy.MAX_WAITING - 1)).isNull();
    }

    @Test
    void blockReason_모집중신청_예비수와무관하게null() {
        // when & then — 모집 중이면 바로 매칭이라 예비 상한을 보지 않는다
        assertThat(applyPolicy.blockReason(request(1L, NOON), List.of(), ApplyPolicy.MAX_WAITING)).isNull();
    }

    @Test
    void blockReason_예비가다찼고재지원이거나겹침_도우미본인사유가먼저() {
        // given
        HelpRequest target = matchedRequest(1L, NOON);
        HelpRequest other = matchedRequest(2L, NOON.plusMinutes(30));

        // when & then
        assertThat(applyPolicy.blockReason(target, List.of(waiting(target)), ApplyPolicy.MAX_WAITING))
                .isEqualTo(ApplyBlockReason.ALREADY_APPLIED);
        assertThat(applyPolicy.blockReason(target, List.of(matched(other)), ApplyPolicy.MAX_WAITING))
                .isEqualTo(ApplyBlockReason.TIME_OVERLAP);
    }

    @Test
    void overlapsConfirmed_같은신청의지원은보지않고_다른신청확정매칭만본다() {
        // given — 승격 후보 확인: 이 신청의 내 예비는 무시, 다른 신청 매칭과 30분 겹침
        HelpRequest target = request(1L, NOON);
        HelpRequest other = request(2L, NOON.plusMinutes(30));

        // when & then
        assertThat(applyPolicy.overlapsConfirmed(target, List.of(waiting(target)))).isFalse();
        assertThat(applyPolicy.overlapsConfirmed(target, List.of(waiting(target), matched(other)))).isTrue();
        assertThat(applyPolicy.overlapsConfirmed(target, List.of(waiting(target), waiting(other)))).isFalse();
    }
}
