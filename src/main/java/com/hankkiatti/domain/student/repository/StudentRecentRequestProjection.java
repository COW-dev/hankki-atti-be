package com.hankkiatti.domain.student.repository;

import java.time.LocalDateTime;

public interface StudentRecentRequestProjection {

    Long getStudentAccountId();

    LocalDateTime getRecentRequestAt();
}
