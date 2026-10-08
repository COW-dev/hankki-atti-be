package com.hankkiatti.domain.helprequest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.helprequest.dto.response.TimeOptionDateResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.TimeOptionResponseDto;
import com.hankkiatti.domain.helprequest.entity.Meal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeOptionServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private TimeOptionService serviceAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
        return new TimeOptionService(new HelpRequestSchedule(), clock);
    }

    @Test
    void getTimeOptions_날짜별로묶고끝시각과식사구분을채움() {
        // given — 10-08(목) 16:00. 오늘은 저녁만 남고, 10-09 한글날과 주말은 빠진다
        LocalDate today = LocalDate.of(2026, 10, 8);

        // when
        List<TimeOptionDateResponseDto> result = serviceAt(today.atTime(16, 0)).getTimeOptions();

        // then
        assertThat(result).extracting(TimeOptionDateResponseDto::date).containsExactly(
                today, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14),
                LocalDate.of(2026, 10, 15));
        assertThat(result.get(0).startTimes()).containsExactly(
                new TimeOptionResponseDto(today.atTime(17, 0), today.atTime(18, 0), Meal.DINNER),
                new TimeOptionResponseDto(today.atTime(17, 30), today.atTime(18, 30), Meal.DINNER));
        assertThat(result.get(1).startTimes()).hasSize(6)
                .first().extracting(TimeOptionResponseDto::meal).isEqualTo(Meal.LUNCH);
    }

    @Test
    void getTimeOptions_고를시각이없는오늘_목록에없음() {
        // given
        LocalDate today = LocalDate.of(2026, 10, 12);

        // when
        List<TimeOptionDateResponseDto> result = serviceAt(today.atTime(18, 0)).getTimeOptions();

        // then
        assertThat(result).extracting(TimeOptionDateResponseDto::date).doesNotContain(today);
        assertThat(result).allSatisfy(date -> assertThat(date.startTimes()).isNotEmpty());
    }
}
