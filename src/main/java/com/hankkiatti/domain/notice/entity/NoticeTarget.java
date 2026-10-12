package com.hankkiatti.domain.notice.entity;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.common.LabeledEnum;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공지 대상. 아띠는 아띠 소속 도우미만 (가입 때 자기신고, 조회 시점 기준) — 관리자 화면에서 쓰지 않아도 다른 대상에 영향 없다.
 */
@Getter
@RequiredArgsConstructor
public enum NoticeTarget implements LabeledEnum {

    ALL("전체"),
    STUDENT("장애학생"),
    HELPER("도우미"),
    ATTI("아띠");

    private final String label;

    /**
     * 이 사용자가 볼 수 있는 대상. 관리자는 사용자 공지 화면을 쓰지 않으므로 없다.
     */
    public static List<NoticeTarget> visibleTo(AccountRole role, boolean attiMember) {
        return switch (role) {
            case STUDENT -> List.of(ALL, STUDENT);
            case HELPER -> attiMember ? List.of(ALL, HELPER, ATTI) : List.of(ALL, HELPER);
            case ADMIN -> List.of();
        };
    }
}
