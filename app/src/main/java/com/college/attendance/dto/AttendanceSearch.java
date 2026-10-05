package com.college.attendance.dto;

import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.WorkflowStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * The five optional filters from FR-13, combined with AND semantics.
 *
 * <p>Carried as one object so that the controller can echo the active
 * filters straight back into the pagination links, which is what keeps
 * them alive across pages (FR-14).
 */
public class AttendanceSearch {

    private String rollNumber;
    private String subjectCode;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    private AttendanceStatus attendanceStatus;
    private WorkflowStatus workflowStatus;

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = blankToNull(rollNumber);
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        String value = blankToNull(subjectCode);
        this.subjectCode = value == null ? null : value.toUpperCase();
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public AttendanceStatus getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(AttendanceStatus attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    public WorkflowStatus getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(WorkflowStatus workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    /** Whether the user has actually narrowed anything. */
    public boolean isActive() {
        return rollNumber != null || subjectCode != null || from != null || to != null
                || attendanceStatus != null || workflowStatus != null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
