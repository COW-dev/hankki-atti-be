package com.hankkiatti.domain.application.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ApplicationAfterActionConverter extends AbstractEnumConverter<ApplicationAfterAction> {

    public ApplicationAfterActionConverter() {
        super(ApplicationAfterAction.class);
    }
}
