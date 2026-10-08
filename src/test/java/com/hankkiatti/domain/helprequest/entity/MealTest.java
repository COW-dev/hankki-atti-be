package com.hankkiatti.domain.helprequest.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class MealTest {

    @Test
    void of_시작시각으로식사구분_선택지에없는시각은없음() {
        // when & then
        assertThat(Meal.of(LocalTime.of(11, 30))).contains(Meal.LUNCH);
        assertThat(Meal.of(LocalTime.of(13, 0))).contains(Meal.LUNCH);
        assertThat(Meal.of(LocalTime.of(17, 30))).contains(Meal.DINNER);
        assertThat(Meal.of(LocalTime.of(13, 30))).isEmpty();
        assertThat(Meal.of(LocalTime.of(18, 0))).isEmpty();
    }
}
