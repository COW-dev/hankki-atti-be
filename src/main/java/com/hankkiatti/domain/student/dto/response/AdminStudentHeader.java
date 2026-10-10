package com.hankkiatti.domain.student.dto.response;

/**
 * 장애학생 상세 화면 위 프로필 (이름·학번·상태 + 전체 권한만 장애 유형). 제한 권한도 매칭현황·취소·노쇼 이력 탭에서 보므로
 * 두 탭 응답에 같이 준다. 제한 권한 응답에는 장애 유형을 null로 두지 않고 필드 자체를 넣지 않는다.
 */
public sealed interface AdminStudentHeader permits AdminStudentHeaderResponseDto, LimitedAdminStudentHeaderResponseDto {
}
