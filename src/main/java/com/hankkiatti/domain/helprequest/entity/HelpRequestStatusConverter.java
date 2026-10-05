package com.hankkiatti.domain.helprequest.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class HelpRequestStatusConverter extends AbstractEnumConverter<HelpRequestStatus> {

    public HelpRequestStatusConverter() {
        super(HelpRequestStatus.class);
    }
}
