package com.college.attendance.service;

import com.college.attendance.config.AttendanceProperties;
import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.WorkflowStatus;
import com.college.attendance.dto.DashboardSummary;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.AttendanceSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Aggregates the summary dashboard (FR-23, FR-24, US-20).
 *
 * <p>Two rules drive every figure here:
 *
 * <ul>
 *   <li><b>BR-04</b> — only {@code APPROVED} records contribute to a
 *       published percentage. A draft or a rejected record is not yet a
 *       fact about a student, and showing it as one is exactly the kind
 *       of premature number the paper process produced.</li>
 *   <li><b>BR-03</b> — {@code EXCUSED} leaves both the numerator and the
 *       denominator. Counting approved leave as an absence would punish
 *       the student for a certificate the institution accepted.</li>
 * </ul>
 */
@Service
public class DashboardService {

    private final AttendanceRecordRepository records;
    private final CurrentUser currentUser;
    private final AttendanceProperties properties;

    public DashboardService(AttendanceRecordRepository records, CurrentUser currentUser,
                            AttendanceProperties properties) {
        this.records = records;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    /**
     * The summary for the signed-in user: department-wide for staff,
     * restricted to their own approved records for a student (FR-25).
     */
    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        Optional<String> ownRollNumber = currentUser.restrictedToRollNumber();
        int threshold = properties.eligibilityThreshold();

        List<AttendanceRecord> visible = ownRollNumber
                .map(roll -> records.findAll(AttendanceSpecifications.rollNumber(roll)))
                .orElseGet(records::findAll);

        if (visible.isEmpty()) {
            return DashboardSummary.empty(threshold);
        }

        // A student only ever sees official records (BR-04, FR-25).
        List<AttendanceRecord> forStudentView = ownRollNumber.isPresent()
                ? visible.stream().filter(r -> r.getWorkflowStatus() == WorkflowStatus.APPROVED).toList()
                : visible;

        long draft = count(visible, WorkflowStatus.DRAFT);
        long submitted = count(visible, WorkflowStatus.SUBMITTED);
        long approved = count(visible, WorkflowStatus.APPROVED);
        long rejected = count(visible, WorkflowStatus.REJECTED);

        List<AttendanceRecord> official = visible.stream()
                .filter(r -> r.getWorkflowStatus() == WorkflowStatus.APPROVED)
                .toList();

        long counted = official.stream()
                .filter(r -> r.getAttendanceStatus().countsTowardsTotal())
                .count();
        long attended = official.stream()
                .filter(r -> r.getAttendanceStatus().countsTowardsTotal())
                .filter(r -> r.getAttendanceStatus().countsAsAttended())
                .count();

        return new DashboardSummary(
                forStudentView.size(),
                draft, submitted, approved, rejected,
                counted, attended, percent(attended, counted),
                threshold,
                subjectBreakdown(official),
                atRisk(official, threshold, ownRollNumber.isPresent()));
    }

    private static long count(List<AttendanceRecord> records, WorkflowStatus status) {
        return records.stream().filter(r -> r.getWorkflowStatus() == status).count();
    }

    private List<DashboardSummary.SubjectBreakdown> subjectBreakdown(List<AttendanceRecord> official) {
        Map<String, long[]> bySubject = new LinkedHashMap<>();
        official.stream()
                .sorted(Comparator.comparing(AttendanceRecord::getSubjectCode))
                .forEach(r -> {
                    if (!r.getAttendanceStatus().countsTowardsTotal()) {
                        return;
                    }
                    long[] tally = bySubject.computeIfAbsent(r.getSubjectCode(), k -> new long[2]);
                    tally[0]++;
                    if (r.getAttendanceStatus().countsAsAttended()) {
                        tally[1]++;
                    }
                });

        return bySubject.entrySet().stream()
                .map(e -> new DashboardSummary.SubjectBreakdown(
                        e.getKey(), e.getValue()[0], e.getValue()[1],
                        percent(e.getValue()[1], e.getValue()[0])))
                .toList();
    }

    /**
     * Students below the eligibility threshold.
     *
     * <p>Deliberately empty for a student's own dashboard: a student is
     * shown their own standing in the headline figure, and has no business
     * seeing a list of their classmates who are failing.
     */
    private List<DashboardSummary.StudentStanding> atRisk(List<AttendanceRecord> official,
                                                          int threshold, boolean studentView) {
        if (studentView) {
            return List.of();
        }

        record Tally(String name, long counted, long attended) {
        }
        Map<String, Tally> byStudent = new LinkedHashMap<>();
        for (AttendanceRecord r : official) {
            if (!r.getAttendanceStatus().countsTowardsTotal()) {
                continue;
            }
            String roll = r.getStudent().getRollNumber();
            Tally current = byStudent.getOrDefault(roll,
                    new Tally(r.getStudent().getFullName(), 0, 0));
            byStudent.put(roll, new Tally(current.name(), current.counted() + 1,
                    current.attended() + (r.getAttendanceStatus().countsAsAttended() ? 1 : 0)));
        }

        return byStudent.entrySet().stream()
                .map(e -> new DashboardSummary.StudentStanding(
                        e.getKey(), e.getValue().name(),
                        e.getValue().counted(), e.getValue().attended(),
                        percent(e.getValue().attended(), e.getValue().counted())))
                .filter(s -> s.percent() < threshold)
                .sorted(Comparator.comparingDouble(DashboardSummary.StudentStanding::percent))
                .toList();
    }

    /** Rounded to one decimal place; zero sessions is 0%, not a division by zero. */
    private static double percent(long attended, long counted) {
        if (counted == 0) {
            return 0.0;
        }
        return Math.round(attended * 1000.0 / counted) / 10.0;
    }
}
