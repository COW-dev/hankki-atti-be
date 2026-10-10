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

    // 010-1234-5678 / 01012345678 → +821012345678 (국제 표준 E.164). 문자 발송에 쓴다
    public static String toE164(String raw) {
        String digits = raw.replace("-", "").trim();
        return "+82" + digits.substring(1);
    }

    // 010-1234-5678 → 010-****-5678. 목록처럼 번호가 필요 없는 화면에 준다 (상세에서 전체 번호)
    public static String mask(String stored) {
        String[] parts = stored.split("-");
        if (parts.length != 3) {
            return "*".repeat(stored.length());
        }
        return parts[0] + "-" + "*".repeat(parts[1].length()) + "-" + parts[2];
    }
}
