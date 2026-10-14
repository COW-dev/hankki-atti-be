package com.hankkiatti.domain.common;

/**
 * 관리자 목록 화면에 주는 개인정보를 일부 가린다. 전체 값은 상세에서 준다. 전화번호는 {@link PhoneNumbers#mask}.
 */
public final class PersonalInfoMasks {

    private PersonalInfoMasks() {}

    // student@mju.ac.kr → st*****@mju.ac.kr (도메인은 그대로)
    public static String email(String email) {
        int separator = email.indexOf('@');
        if (separator <= 0) {
            return identifier(email);
        }
        return identifier(email.substring(0, separator)) + email.substring(separator);
    }

    // hankki_student → ha************. 짧은 값도 길이를 짐작하지 못하게 최소 3자를 가린다
    public static String identifier(String value) {
        int visibleLength = Math.min(2, value.length());
        int maskedLength = Math.max(3, value.length() - visibleLength);
        return value.substring(0, visibleLength) + "*".repeat(maskedLength);
    }
}
