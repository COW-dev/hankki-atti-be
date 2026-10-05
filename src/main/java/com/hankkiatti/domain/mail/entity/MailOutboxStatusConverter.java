package com.hankkiatti.domain.mail.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MailOutboxStatusConverter extends AbstractEnumConverter<MailOutboxStatus> {

    public MailOutboxStatusConverter() {
        super(MailOutboxStatus.class);
    }
}
