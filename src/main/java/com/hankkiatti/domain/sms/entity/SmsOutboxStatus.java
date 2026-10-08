package com.hankkiatti.domain.sms.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SmsOutboxStatus implements LabeledEnum {

    PENDING("발송 대기"),
    SENDING("발송 중"),
    SENT("발송 완료"),
    FAILED("발송 실패");

    private final String label;
}
