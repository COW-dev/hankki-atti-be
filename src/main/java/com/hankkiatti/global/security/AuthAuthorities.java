package com.hankkiatti.global.security;

import com.hankkiatti.domain.account.entity.AccountRole;

public final class AuthAuthorities {

    // 비밀번호 변경이 필요한 계정은 역할 권한 대신 이 권한만 받는다 → 비밀번호 변경 API만 통과
    public static final String PASSWORD_CHANGE_ONLY = "PASSWORD_CHANGE_ONLY";

    private AuthAuthorities() {}

    public static String role(AccountRole role) {
        return "ROLE_" + role.name();
    }
}
