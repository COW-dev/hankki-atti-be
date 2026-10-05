package com.hankkiatti.domain.auth.entity;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 토큰을 쓸 수 있는 앱. 사용자 앱 토큰으로 관리자 API를, 관리자 토큰으로 사용자 API를 호출할 수 없게 나눈다.
 */
@Getter
@RequiredArgsConstructor
public enum TokenAudience implements LabeledEnum {

    USER("user", "사용자 앱"),
    ADMIN("admin", "관리자 페이지");

    private final String claimValue;
    private final String label;

    public boolean allows(AccountRole role) {
        return this == ADMIN ? role == AccountRole.ADMIN : role != AccountRole.ADMIN;
    }

    public static TokenAudience fromClaim(String claimValue) {
        for (TokenAudience audience : values()) {
            if (audience.claimValue.equals(claimValue)) {
                return audience;
            }
        }
        return null;
    }
}
