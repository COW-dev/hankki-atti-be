package com.hankkiatti.domain.sms.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 문자 수신 동의가 정해지거나 바뀐 경로.
 */
@Getter
@RequiredArgsConstructor
public enum SmsConsentSource implements LabeledEnum {
    // 도우미 회원가입 때 체크
    SIGNUP("가입"),
    // 센터가 장애학생 등록·정보 수정 때 체크 (장애학생에게 동의를 받고)
    ADMIN("관리자"),
    // 본인이 마이페이지에서 변경
    SELF("본인");

    private final String label;
}
