package com.hankkiatti.domain.helprequest.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HelpRequestStatus implements LabeledEnum {

    RECRUITING("모집 중"),
    MATCHED("매칭 완료"),
    FAILED("매칭 실패"),
    CANCELED("취소 완료"),
    COMPLETED("이용 완료"),
    NO_SHOW("노쇼");

    // 진행 중인 신청: 아직 끝나지 않았고 취소·실패도 아니다. 내 신청의 "다가오는 신청", 장애학생 시간 겹침 검사 대상
    public static final List<HelpRequestStatus> IN_PROGRESS = List.of(RECRUITING, MATCHED);

    private final String label;

    public boolean isInProgress() {
        return IN_PROGRESS.contains(this);
    }
}
