package com.hankkiatti.domain.notice.dto.request;

import com.hankkiatti.domain.notice.entity.Notice;
import com.hankkiatti.domain.notice.entity.NoticeTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "공지 작성·수정 요청")
public record AdminNoticeSaveRequestDto(

        @NotBlank
        @Size(max = Notice.TITLE_MAX_LENGTH)
        @Schema(description = "제목 (100자 이내, 앞뒤 공백은 지워 저장)", example = "10월 학식당 운영 시간 안내")
        String title,

        @NotBlank
        @Size(max = Notice.BODY_MAX_LENGTH)
        @Schema(description = "본문 (일반 텍스트 5,000자 이내, 줄바꿈 그대로 저장). 링크는 URL을 그대로 쓰면 화면이 연결한다",
                example = "10월부터 학생식당 점심 운영 시간이 바뀌어요.\n\n• 평일 11:00 ~ 14:00")
        String body,

        @NotNull
        @Schema(description = "대상 — ALL 전체, STUDENT 장애학생, HELPER 도우미, ATTI 아띠 소속 도우미", example = "ALL")
        NoticeTarget target,

        @Schema(description = "목록 맨 위 고정. 보내지 않으면 false", example = "true")
        Boolean pinned
) {}
