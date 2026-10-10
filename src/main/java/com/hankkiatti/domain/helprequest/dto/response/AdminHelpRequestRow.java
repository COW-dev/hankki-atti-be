package com.hankkiatti.domain.helprequest.dto.response;

/**
 * 관리자 전체 신청 현황의 한 줄. 관리자 권한 등급마다 담는 필드가 달라 응답 타입을 나눈다 —
 * 제한 권한 응답에는 장애 유형을 null로 두지 않고 필드 자체를 넣지 않는다 (요구사항 관리자 권한 매트릭스).
 */
public sealed interface AdminHelpRequestRow permits AdminHelpRequestResponseDto, LimitedAdminHelpRequestResponseDto {
}
