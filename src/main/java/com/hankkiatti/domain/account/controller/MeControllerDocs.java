package com.hankkiatti.domain.account.controller;

import com.hankkiatti.domain.account.dto.request.MeContactUpdateRequestDto;
import com.hankkiatti.domain.account.dto.request.MeSettingsUpdateRequestDto;
import com.hankkiatti.domain.account.dto.response.MeResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "내 정보", description = "로그인한 장애학생·도우미의 내 정보 (마이페이지)")
public interface MeControllerDocs {

    @Operation(summary = "내 정보 조회", description = """
            역할·접근성 모드와 마이페이지에 보여 줄 이름·학번·전화번호·카톡 ID를 준다.
            도우미면 helper에 아띠 소속과 봉사시간 누적이 들어 있고, 장애학생은 helper가 null이다.
            장애 유형·특이사항은 넣지 않는다. 비밀번호 변경이 필요한 계정은 403.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "비밀번호 변경 필요")
    ResponseEntity<ApiResult<MeResponseDto>> getMe(@Parameter(hidden = true) AuthPrincipal principal);

    @Operation(summary = "내 설정 변경",
            description = "접근성 모드(큰 글씨 + 음성 읽기) 켜기·끄기를 계정에 저장한다. 다른 기기에서 로그인해도 같은 설정이 적용된다. "
                    + "응답은 바뀐 내 계정 정보.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "비밀번호 변경 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "accessibilityMode 값이 없음")
    ResponseEntity<ApiResult<MeResponseDto>> updateSettings(
            @Parameter(hidden = true) AuthPrincipal principal,
            MeSettingsUpdateRequestDto request);

    @Operation(summary = "도우미 연락처 수정", description = """
            도우미가 자기 전화번호·카톡 ID를 바꾼다. 둘 다 보낸다 (지금 값을 채운 폼).
            전화번호는 하이픈 형식(010-1234-5678)으로 맞춰 저장한다. 응답은 바뀐 내 정보.
            장애학생 연락처는 센터가 관리하므로 장애학생이 부르면 403.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "장애학생 계정이거나 비밀번호 변경 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "전화번호 형식 오류, 카톡 ID에 공백 또는 50자 초과")
    ResponseEntity<ApiResult<MeResponseDto>> updateContact(
            @Parameter(hidden = true) AuthPrincipal principal,
            MeContactUpdateRequestDto request);
}
