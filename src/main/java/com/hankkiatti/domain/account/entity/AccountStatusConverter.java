package com.hankkiatti.domain.account.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AccountStatusConverter extends AbstractEnumConverter<AccountStatus> {

    public AccountStatusConverter() {
        super(AccountStatus.class);
    }
}
