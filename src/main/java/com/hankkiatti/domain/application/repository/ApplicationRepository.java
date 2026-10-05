package com.hankkiatti.domain.application.repository;

import com.hankkiatti.domain.application.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
}
