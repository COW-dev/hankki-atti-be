package com.hankkiatti.domain.application.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CancelReasonConverter extends AbstractEnumConverter<CancelReason> {

    public CancelReasonConverter() {
        super(CancelReason.class);
    }
}
