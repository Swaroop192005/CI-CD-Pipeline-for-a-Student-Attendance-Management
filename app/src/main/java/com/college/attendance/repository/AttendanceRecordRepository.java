package com.college.attendance.repository;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.WorkflowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for attendance records.
 *
 * <p>Extends {@link JpaSpecificationExecutor} because the search
 * requirement (FR-13) combines five optional filters with AND semantics;
 * writing that as derived query methods would need one method per
 * combination, whereas a specification composes them.
 */
public interface AttendanceRecordRepository
        extends JpaRepository<AttendanceRecord, Long>, JpaSpecificationExecutor<AttendanceRecord> {

    /** Default list ordering: most recent session first, then period (FR-08). */
    Page<AttendanceRecord> findAllByOrderBySessionDateDescPeriodNumberAsc(Pageable pageable);

    boolean existsByStudent_RollNumberAndSubjectCodeAndSessionDateAndPeriodNumber(
            String rollNumber, String subjectCode, LocalDate sessionDate, int periodNumber);

    List<AttendanceRecord> findByWorkflowStatusOrderBySessionDateDesc(WorkflowStatus workflowStatus);

    long countByWorkflowStatus(WorkflowStatus workflowStatus);

    List<AttendanceRecord> findByStudent_RollNumberAndWorkflowStatus(
            String rollNumber, WorkflowStatus workflowStatus);

    /** Distinct subject codes present in the data, for filter drop-downs. */
    @Query("select distinct r.subjectCode from AttendanceRecord r order by r.subjectCode")
    List<String> findDistinctSubjectCodes();
}
