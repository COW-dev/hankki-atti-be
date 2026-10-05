package com.hankkiatti.domain.application.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ApplicationStatusConverter extends AbstractEnumConverter<ApplicationStatus> {

    public ApplicationStatusConverter() {
        super(ApplicationStatus.class);
    }
}
