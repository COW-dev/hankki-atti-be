package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.dto.request.VolunteerHoursUpdateRequestDto;
import com.hankkiatti.domain.application.dto.response.AdminVolunteerHoursResponseDto;
import com.hankkiatti.domain.application.dto.response.VolunteerHourChangeResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.VolunteerHourChange;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.repository.VolunteerHourChangeRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 봉사시간 수정 (Notion 기능명세서 "봉사시간 수정"). 전체 권한만, 이용 완료·노쇼 건을 0.5시간 단위로 고치고 이력을 남긴다.
 * 도우미 봉사시간 합계는 지원 행의 합이라 따로 다시 계산할 것이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminVolunteerHourService {

    private static final BigDecimal UNIT = new BigDecimal("0.5");

    private final AdminRepository adminRepository;
    private final ApplicationRepository applicationRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final VolunteerHourChangeRepository volunteerHourChangeRepository;

    /**
     * 락 순서: 신청 → 지원. 노쇼 신고도 신청을 잠그고 봉사시간을 0으로 바꾸므로 둘이 한 줄로 서서 서로 덮어쓰지 않는다.
     * 지원 ID로 시작해 신청 ID를 값으로 먼저 읽으므로 도우미 취소처럼 READ_COMMITTED로 둔다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminVolunteerHoursResponseDto change(Long adminAccountId, Long applicationId,
                                                 VolunteerHoursUpdateRequestDto request) {
        Admin admin = adminRepository.findById(adminAccountId)
                .orElseThrow(() -> new AuthException(AuthErrorType.ACCESS_DENIED, "adminAccountId=" + adminAccountId));
        if (!admin.isFull()) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED,
                    "봉사시간 수정은 전체 권한만, adminAccountId=" + adminAccountId);
        }
        if (request.hours().remainder(UNIT).signum() != 0) {
            throw new ApplicationException(ApplicationErrorType.INVALID_VOLUNTEER_HOURS, "applicationId=" + applicationId);
        }
        // 0.5 단위라 소수 첫째 자리로 딱 맞는다 (1.50 → 1.5)
        BigDecimal after = request.hours().setScale(1, RoundingMode.UNNECESSARY);

        Long helpRequestId = applicationRepository.findHelpRequestIdById(applicationId)
                .orElseThrow(() -> notFound(applicationId));
        helpRequestRepository.findByIdForUpdate(helpRequestId).orElseThrow(() -> notFound(applicationId));
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> notFound(applicationId));

        BigDecimal before = application.getVolunteerHours();
        if (before != null && before.compareTo(after) == 0) {
            throw new ApplicationException(ApplicationErrorType.VOLUNTEER_HOURS_UNCHANGED,
                    "applicationId=" + applicationId);
        }
        application.changeVolunteerHours(after);
        VolunteerHourChange change = volunteerHourChangeRepository.save(
                new VolunteerHourChange(application, admin, before, after, request.reason().trim()));

        // 사유는 개인 사정이 들어갈 수 있어 남기지 않는다
        log.info("봉사시간 수정: applicationId={}, adminAccountId={}, before={}, after={}",
                applicationId, adminAccountId, before, after);
        return new AdminVolunteerHoursResponseDto(application.getId(), application.getVolunteerHours(),
                toResponse(change));
    }

    public static VolunteerHourChangeResponseDto toResponse(VolunteerHourChange change) {
        return new VolunteerHourChangeResponseDto(change.getId(), change.getBeforeHours(), change.getAfterHours(),
                change.getReason(), change.getAdmin().getName(), change.getCreatedAt());
    }

    private static ApplicationException notFound(Long applicationId) {
        return new ApplicationException(ApplicationErrorType.NOT_FOUND, "applicationId=" + applicationId);
    }
}
