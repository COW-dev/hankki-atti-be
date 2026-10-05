package com.hankkiatti.domain.student.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DisabilityTypeConverter extends AbstractEnumConverter<DisabilityType> {

    public DisabilityTypeConverter() {
        super(DisabilityType.class);
    }
}
