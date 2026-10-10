package com.hankkiatti.domain.application.repository;

/**
 * 도우미별 지원 수 (관리자 도우미 목록의 이용 완료 건수).
 */
public record HelperApplicationCount(Long helperId, long count) {}
