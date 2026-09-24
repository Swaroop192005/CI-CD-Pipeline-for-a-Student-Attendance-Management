package com.college.attendance.repository;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.WorkflowStatus;
import com.college.attendance.dto.AttendanceSearch;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Composable predicates for the attendance search (FR-13).
 *
 * <p>Specifications rather than derived query methods because the five
 * filters are independently optional: as derived methods that would need
 * one signature per combination. Composing also means the student-scoping
 * predicate can be AND-ed on top of whatever the user asked for, so a
 * student's own filters can never widen their view beyond their own
 * records (FR-25).
 */
public final class AttendanceSpecifications {

    private AttendanceSpecifications() {
    }

    /** Builds the AND-combination of whichever filters were supplied. */
    public static Specification<AttendanceRecord> matching(AttendanceSearch search) {
        Specification<AttendanceRecord> spec = Specification.unrestricted();
        if (search == null) {
            return spec;
        }
        return spec.and(rollNumber(search.getRollNumber()))
                .and(subjectCode(search.getSubjectCode()))
                .and(sessionDateFrom(search.getFrom()))
                .and(sessionDateTo(search.getTo()))
                .and(attendanceStatus(search.getAttendanceStatus()))
                .and(workflowStatus(search.getWorkflowStatus()));
    }

    public static Specification<AttendanceRecord> rollNumber(String rollNumber) {
        return (root, query, cb) -> rollNumber == null
                ? null
                : cb.equal(root.get("student").get("rollNumber"), rollNumber);
    }

    public static Specification<AttendanceRecord> subjectCode(String subjectCode) {
        return (root, query, cb) -> subjectCode == null
                ? null
                : cb.equal(cb.upper(root.get("subjectCode")), subjectCode.toUpperCase());
    }

    /** Inclusive lower bound (FR-13 AC-3). */
    public static Specification<AttendanceRecord> sessionDateFrom(LocalDate from) {
        return (root, query, cb) -> from == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("sessionDate"), from);
    }

    /** Inclusive upper bound (FR-13 AC-3). */
    public static Specification<AttendanceRecord> sessionDateTo(LocalDate to) {
        return (root, query, cb) -> to == null
                ? null
                : cb.lessThanOrEqualTo(root.get("sessionDate"), to);
    }

    public static Specification<AttendanceRecord> attendanceStatus(AttendanceStatus status) {
        return (root, query, cb) -> status == null
                ? null
                : cb.equal(root.get("attendanceStatus"), status);
    }

    public static Specification<AttendanceRecord> workflowStatus(WorkflowStatus status) {
        return (root, query, cb) -> status == null
                ? null
                : cb.equal(root.get("workflowStatus"), status);
    }
}
