package com.hankkiatti.domain.helprequest.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 식사 구분과 고를 수 있는 시작 시각. 학식당 운영 시간(중식 11:30~14:00, 석식 17:00~18:30) 안에서
 * 1시간 이용이 끝나야 하므로 시작 시각은 아래와 같다 (기능명세서 "도우미 신청", 2026-09-28 확인).
 * 저장하지 않고 시작 시각에서 구한다.
 */
@Getter
@RequiredArgsConstructor
public enum Meal implements LabeledEnum {

    LUNCH("점심", List.of(LocalTime.of(11, 30), LocalTime.of(12, 0), LocalTime.of(12, 30), LocalTime.of(13, 0))),
    DINNER("저녁", List.of(LocalTime.of(17, 0), LocalTime.of(17, 30)));

    private final String label;
    private final List<LocalTime> startTimes;

    public static Optional<Meal> of(LocalTime startTime) {
        return Arrays.stream(values())
                .filter(meal -> meal.startTimes.contains(startTime))
                .findFirst();
    }
}
