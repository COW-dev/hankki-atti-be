package com.hankkiatti.domain.helprequest.service;

import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helprequest.dto.response.TimeOptionDateResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.TimeOptionResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.Meal;
import com.hankkiatti.domain.student.repository.StudentRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 도우미 신청 화면의 날짜·시작 시각 선택지. 고를 수 있는 시각이 있는 날짜만 날짜 순으로 묶어 준다.
 */
@Service
@RequiredArgsConstructor
public class TimeOptionService {

    private final HelpRequestSchedule helpRequestSchedule;
    private final StudentRepository studentRepository;
    private final Clock clock;

    // 신청 화면에서만 쓰므로 장애학생만 부른다 (다른 장애학생 API와 같은 기준)
    public List<TimeOptionDateResponseDto> getTimeOptions(Long accountId) {
        if (!studentRepository.existsById(accountId)) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED, "장애학생 전용, accountId=" + accountId);
        }
        Map<LocalDate, List<TimeOptionResponseDto>> byDate = helpRequestSchedule
                .bookableStartTimes(LocalDateTime.now(clock)).stream()
                .collect(Collectors.groupingBy(LocalDateTime::toLocalDate, LinkedHashMap::new,
                        Collectors.mapping(this::toOption, Collectors.toList())));
        return byDate.entrySet().stream()
                .map(entry -> new TimeOptionDateResponseDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private TimeOptionResponseDto toOption(LocalDateTime startAt) {
        // 선택지는 Meal의 시각으로만 만들어지므로 식사 구분이 항상 있다
        Meal meal = Meal.of(startAt.toLocalTime()).orElseThrow();
        return new TimeOptionResponseDto(startAt, startAt.plusHours(HelpRequest.USAGE_HOURS), meal);
    }
}
