package com.hankkiatti.domain.application.repository;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByHelpRequestIdAndStatus(Long helpRequestId, ApplicationStatus status);
}
