package com.hankkiatti.domain.admin.repository;

import com.hankkiatti.domain.admin.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {
}
