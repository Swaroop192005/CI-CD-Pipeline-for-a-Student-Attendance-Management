package com.college.attendance.repository;

import com.college.attendance.domain.AttendanceSession;
import com.college.attendance.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    List<AttendanceSession> findByStatusOrderByOpenedAtDesc(SessionStatus status);

    Optional<AttendanceSession> findBySubjectCodeAndSessionDateAndPeriodNumber(
            String subjectCode, LocalDate sessionDate, int periodNumber);

    boolean existsBySubjectCodeAndSessionDateAndPeriodNumber(
            String subjectCode, LocalDate sessionDate, int periodNumber);

    List<AttendanceSession> findTop20ByOrderByOpenedAtDesc();
}
