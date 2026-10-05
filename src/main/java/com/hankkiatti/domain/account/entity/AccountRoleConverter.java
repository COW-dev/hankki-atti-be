package com.hankkiatti.domain.account.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AccountRoleConverter extends AbstractEnumConverter<AccountRole> {

    public AccountRoleConverter() {
        super(AccountRole.class);
    }
}
