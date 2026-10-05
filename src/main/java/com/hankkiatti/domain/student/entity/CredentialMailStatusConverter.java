package com.hankkiatti.domain.student.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CredentialMailStatusConverter extends AbstractEnumConverter<CredentialMailStatus> {

    public CredentialMailStatusConverter() {
        super(CredentialMailStatus.class);
    }
}
