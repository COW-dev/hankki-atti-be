package com.hankkiatti.domain.helprequest.service;

import com.hankkiatti.domain.helprequest.entity.Meal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 장애학생이 도우미를 신청할 수 있는 날짜·시작 시각. 시작 시각 선택지와 신청 생성 검증이 같이 쓴다.
 * <ul>
 *   <li>시작 시각: {@link Meal}의 점심·저녁 시각 (30분 단위, 이용 1시간)</li>
 *   <li>오늘부터 7일 뒤까지, 학식당이 여는 날만 — 주말·공휴일 제외 (PM 확인 2026-10-08)</li>
 *   <li>오늘은 아직 시작 전인 시각만</li>
 * </ul>
 */
@Component
public class HelpRequestSchedule {

    // 오늘부터 며칠 뒤까지 신청을 받는지 (PM 확인: 1주일)
    public static final int BOOKABLE_DAYS = 7;

    // 평일인 공휴일·대체공휴일. 주말은 따로 빠지므로 적지 않는다.
    // 출처: 한국천문연구원 달력자료(월력요항). 매년 월력요항 발표 때와 임시공휴일이 지정될 때 갱신한다
    private static final Set<LocalDate> HOLIDAYS = Set.of(
            LocalDate.of(2026, 10, 9),   // 한글날
            LocalDate.of(2026, 12, 25),  // 기독탄신일
            // 2027년은 공식 월력요항 발표 전 자료(천문연 2026-06-30 생성)라 발표되면 대조한다
            LocalDate.of(2027, 1, 1),    // 1월 1일
            LocalDate.of(2027, 2, 8),    // 설날
            LocalDate.of(2027, 2, 9),    // 대체공휴일(설날)
            LocalDate.of(2027, 3, 1),    // 3.1절
            LocalDate.of(2027, 5, 3),    // 대체공휴일(노동절)
            LocalDate.of(2027, 5, 5),    // 어린이날
            LocalDate.of(2027, 5, 13),   // 부처님오신날
            LocalDate.of(2027, 7, 19),   // 대체공휴일(제헌절)
            LocalDate.of(2027, 8, 16),   // 대체공휴일(광복절)
            LocalDate.of(2027, 9, 14),   // 추석
            LocalDate.of(2027, 9, 15),   // 추석
            LocalDate.of(2027, 9, 16),   // 추석
            LocalDate.of(2027, 10, 4),   // 대체공휴일(개천절)
            LocalDate.of(2027, 10, 11),  // 대체공휴일(한글날)
            LocalDate.of(2027, 12, 27)   // 대체공휴일(기독탄신일)
    );

    /**
     * 지금 고를 수 있는 시작 시각. 날짜·시각 순이다.
     */
    public List<LocalDateTime> bookableStartTimes(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        return today.datesUntil(today.plusDays(BOOKABLE_DAYS + 1L))
                .filter(this::isOpen)
                .flatMap(date -> Arrays.stream(Meal.values())
                        .flatMap(meal -> meal.getStartTimes().stream())
                        .map(date::atTime))
                .filter(startAt -> startAt.isAfter(now))
                .toList();
    }

    // 공휴일 판단은 여기 한 곳에서만 한다. 공휴일 자동 동기화(BE-82)를 붙이면 상수 대신 저장된 목록을 읽게 바꾼다
    public boolean isHoliday(LocalDate date) {
        return HOLIDAYS.contains(date);
    }

    private boolean isOpen(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY && !isHoliday(date);
    }
}
