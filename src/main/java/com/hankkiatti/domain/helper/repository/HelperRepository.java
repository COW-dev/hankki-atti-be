package com.hankkiatti.domain.helper.repository;

import com.hankkiatti.domain.helper.entity.Helper;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelperRepository extends JpaRepository<Helper, Long> {

    boolean existsByEmail(String email);

    boolean existsByStudentNo(String studentNo);
}
