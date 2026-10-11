package com.hankkiatti.domain.notice.event;

/**
 * 관리자가 공지를 게시했다. 대상에게 인앱 알림을 보내는 데 쓴다 (BE-70). 수정에는 발행하지 않는다 (재알림 없음).
 * 게시 트랜잭션 안에서 발행되므로 알림 작업도 공지와 함께 커밋·롤백된다.
 */
public record NoticePublishedEvent(Long noticeId) {}
