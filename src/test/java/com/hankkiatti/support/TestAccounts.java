package com.hankkiatti.support;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 단위 테스트용 계정. id는 DB가 만들기 때문에 리플렉션으로 넣는다.
 */
public final class TestAccounts {

    private TestAccounts() {}

    public static Account withId(Long id, AccountRole role, String passwordHash, boolean mustChangePassword) {
        Account account = new Account("login-" + id, passwordHash, role, mustChangePassword, false);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
