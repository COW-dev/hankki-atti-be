package com.hankkiatti.domain.notice.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NoticeTargetConverter extends AbstractEnumConverter<NoticeTarget> {

    public NoticeTargetConverter() {
        super(NoticeTarget.class);
    }
}
