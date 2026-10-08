package com.hankkiatti.domain.sms.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 문자 종류. 이메일 알림과 같은 4종 (기능명세서 이메일 알림 + 2026-10-08 문자 요구사항).
 */
@Getter
@RequiredArgsConstructor
public enum SmsType implements LabeledEnum {

    MATCHED("매칭 완료"),
    PROMOTED("예비에서 승격"),
    COUNTERPART_CANCELED("상대방 취소"),
    MATCH_FAILED("매칭 실패");

    private final String label;
}
