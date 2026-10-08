package com.hankkiatti.domain.common;

/**
 * 휴대전화 번호를 저장 형식(010-1234-5678)으로 맞춘다. 형식 검사는 {@code @PhoneNumber}가 먼저 한다.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    // 010-1234-5678 / 01012345678 / 011-123-4567 → 하이픈 형식
    public static String normalize(String raw) {
        String digits = raw.replace("-", "").trim();
        int middleEnd = digits.length() - 4;
        return digits.substring(0, 3) + "-" + digits.substring(3, middleEnd) + "-" + digits.substring(middleEnd);
    }
}
