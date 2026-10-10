package com.hankkiatti.domain.helprequest.service;

import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 식사 날짜로 조회하는 기간 (요청 목록·관리자 전체 신청 현황이 같이 쓴다). 기본은 오늘부터 7일 뒤까지 — 신청이 생길 수 있는 범위 전부.
 * 한 번에 31일까지 조회한다.
 *
 * @param from 시작 날짜
 * @param to   끝 날짜 (포함)
 */
public record HelpRequestDateRange(LocalDate from, LocalDate to) {

    static final int MAX_RANGE_DAYS = 31;

    /**
     * @param from 없으면 오늘
     * @param to   없으면 from + 7일
     */
    public static HelpRequestDateRange of(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate fromDate = from == null ? today : from;
        LocalDate toDate = to == null ? fromDate.plusDays(HelpRequestSchedule.BOOKABLE_DAYS) : to;
        if (toDate.isBefore(fromDate) || ChronoUnit.DAYS.between(fromDate, toDate) >= MAX_RANGE_DAYS) {
            throw new HelpRequestException(HelpRequestErrorType.INVALID_DATE_RANGE,
                    "from=" + fromDate + ", to=" + toDate);
        }
        return new HelpRequestDateRange(fromDate, toDate);
    }

    // 식사 시작 시각 조회 범위 [startInclusive, endExclusive)
    public LocalDateTime startInclusive() {
        return from.atStartOfDay();
    }

    public LocalDateTime endExclusive() {
        return to.plusDays(1).atStartOfDay();
    }
}
