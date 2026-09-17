package com.college.attendance.dto;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Backing object for the create and edit forms.
 *
 * <p>Separate from the entity so that a malformed or hostile form
 * submission cannot reach a managed object, and so that fields the user
 * must never set — workflow status and the audit trail — simply do not
 * exist here to be bound.
 */
public class AttendanceForm {

    private Long id;

    @NotBlank(message = "Select a student")
    private String rollNumber;

    @NotBlank(message = "Subject code is required")
    @Pattern(regexp = "^[A-Z]{2,4}[0-9]{3}$",
            message = "Subject code looks like CS501: two to four letters then three digits")
    private String subjectCode;

    @NotNull(message = "Session date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate sessionDate;

    @Min(value = 1, message = "Period must be between 1 and 8")
    @Max(value = 8, message = "Period must be between 1 and 8")
    private int periodNumber = 1;

    @NotNull(message = "Select an attendance status")
    private AttendanceStatus attendanceStatus;

    @Size(max = 500, message = "Remarks cannot exceed 500 characters")
    private String remarks;

    public AttendanceForm() {
        this.sessionDate = LocalDate.now();
    }

    /** Populates the edit form from an existing record. */
    public static AttendanceForm from(AttendanceRecord record) {
        AttendanceForm form = new AttendanceForm();
        form.id = record.getId();
        form.rollNumber = record.getStudent().getRollNumber();
        form.subjectCode = record.getSubjectCode();
        form.sessionDate = record.getSessionDate();
        form.periodNumber = record.getPeriodNumber();
        form.attendanceStatus = record.getAttendanceStatus();
        form.remarks = record.getRemarks();
        return form;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode == null ? null : subjectCode.trim().toUpperCase();
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public int getPeriodNumber() {
        return periodNumber;
    }

    public void setPeriodNumber(int periodNumber) {
        this.periodNumber = periodNumber;
    }

    public AttendanceStatus getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(AttendanceStatus attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
