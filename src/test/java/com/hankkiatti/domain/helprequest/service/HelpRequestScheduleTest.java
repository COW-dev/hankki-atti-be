package com.hankkiatti.domain.helprequest.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 2026-10-08(목) ~ 10-23(금) 달력 기준. 10-09(금)은 한글날, 10-10·11과 10-17·18은 주말이다.
 */
class HelpRequestScheduleTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

    private final HelpRequestSchedule schedule = new HelpRequestSchedule();

    private List<LocalDateTime> startTimesOn(LocalDate date, LocalDateTime now) {
        return schedule.bookableStartTimes(now).stream()
                .filter(startAt -> startAt.toLocalDate().equals(date))
                .toList();
    }

    private List<LocalDate> datesOf(LocalDateTime now) {
        return schedule.bookableStartTimes(now).stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .toList();
    }

    @Test
    void bookableStartTimes_평일아침_오늘6개부터7일뒤까지평일만() {
        // when
        List<LocalDateTime> result = schedule.bookableStartTimes(MONDAY.atTime(9, 0));

        // then
        assertThat(startTimesOn(MONDAY, MONDAY.atTime(9, 0))).containsExactly(
                MONDAY.atTime(11, 30), MONDAY.atTime(12, 0), MONDAY.atTime(12, 30), MONDAY.atTime(13, 0),
                MONDAY.atTime(17, 0), MONDAY.atTime(17, 30));
        // 7일 뒤(다음 주 월요일)까지, 주말 제외
        assertThat(datesOf(MONDAY.atTime(9, 0))).containsExactly(
                MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2), MONDAY.plusDays(3), MONDAY.plusDays(4),
                MONDAY.plusDays(7));
        assertThat(result).isSorted().hasSize(36);
    }

    @Test
    void bookableStartTimes_점심중간_오늘은아직시작전인시각만() {
        // when & then
        assertThat(startTimesOn(MONDAY, MONDAY.atTime(12, 10))).containsExactly(
                MONDAY.atTime(12, 30), MONDAY.atTime(13, 0), MONDAY.atTime(17, 0), MONDAY.atTime(17, 30));
    }

    @Test
    void bookableStartTimes_시작시각정각_그시각은제외() {
        // when & then
        assertThat(startTimesOn(MONDAY, MONDAY.atTime(12, 30))).first().isEqualTo(MONDAY.atTime(13, 0));
    }

    @Test
    void bookableStartTimes_마지막시각지남_오늘날짜가빠짐() {
        // when & then
        assertThat(datesOf(MONDAY.atTime(17, 30))).first().isEqualTo(MONDAY.plusDays(1));
    }

    @Test
    void bookableStartTimes_금요일_주말건너뛰고7일뒤금요일까지() {
        // given
        LocalDate friday = LocalDate.of(2026, 10, 16);

        // when & then
        assertThat(datesOf(friday.atTime(9, 0))).containsExactly(
                friday, friday.plusDays(3), friday.plusDays(4), friday.plusDays(5), friday.plusDays(6),
                friday.plusDays(7));
    }

    @Test
    void bookableStartTimes_7일뒤가주말_그날은빠짐() {
        // given — 토요일에 보면 7일 뒤도 토요일
        LocalDate saturday = LocalDate.of(2026, 10, 10);

        // when & then
        assertThat(datesOf(saturday.atTime(9, 0))).containsExactly(
                MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2), MONDAY.plusDays(3), MONDAY.plusDays(4));
    }

    @Test
    void bookableStartTimes_평일공휴일_빠짐() {
        // given — 10-08(목)에 보면 다음 날 10-09(금)은 한글날
        LocalDate thursday = LocalDate.of(2026, 10, 8);

        // when & then
        assertThat(datesOf(thursday.atTime(9, 0))).containsExactly(
                thursday, MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2), MONDAY.plusDays(3));
    }

    @Test
    void isHoliday_공휴일과평일() {
        // when & then
        assertThat(schedule.isHoliday(LocalDate.of(2026, 10, 9))).isTrue();
        assertThat(schedule.isHoliday(LocalDate.of(2026, 12, 25))).isTrue();
        assertThat(schedule.isHoliday(MONDAY)).isFalse();
    }
}
