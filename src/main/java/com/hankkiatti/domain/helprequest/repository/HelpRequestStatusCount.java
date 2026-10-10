package com.hankkiatti.domain.helprequest.repository;

import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;

/**
 * 상태별 신청 수 (관리자 전체 신청 현황 요약).
 */
public record HelpRequestStatusCount(HelpRequestStatus status, long count) {}
