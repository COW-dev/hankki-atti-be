package com.hankkiatti.domain.application.repository;

/**
 * 신청별 지원 수 (관리자 전체 신청 현황의 예비 인원).
 */
public record HelpRequestApplicationCount(Long helpRequestId, long count) {}
