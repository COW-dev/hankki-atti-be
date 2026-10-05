package com.hankkiatti.domain.helprequest.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class HelpTypeConverter extends AbstractEnumConverter<HelpType> {

    public HelpTypeConverter() {
        super(HelpType.class);
    }
}
