package com.hankkiatti.domain.helprequest.repository;

import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, Long> {
}
