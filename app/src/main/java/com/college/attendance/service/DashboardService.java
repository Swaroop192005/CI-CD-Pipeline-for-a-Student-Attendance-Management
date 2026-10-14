package com.college.attendance.service;

import com.college.attendance.config.AttendanceProperties;
import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.dto.DashboardSummary;
import com.college.attendance.domain.CaseStatus;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.ShortageCaseRepository;
import com.college.attendance.repository.AttendanceSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
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
 *   <li><b>BR-03</b> — {@code EXCUSED} leaves both the numerator and the
 *       denominator. Counting excused leave as an absence would punish the
 *       student for an explanation the department accepted.</li>
 *   <li><b>BR-08</b> — a student below the shortage threshold is listed
 *       separately from one merely below the eligibility threshold. Being
 *       short of eligibility is common and is the student's own business;
 *       a shortage is rare and is the head of department's.</li>
 * </ul>
 */
@Service
public class DashboardService {

    private final AttendanceRecordRepository records;
    private final ShortageCaseRepository cases;
    private final CurrentUser currentUser;
    private final AttendanceProperties properties;

    public DashboardService(AttendanceRecordRepository records, ShortageCaseRepository cases,
                            CurrentUser currentUser, AttendanceProperties properties) {
        this.records = records;
        this.cases = cases;
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
        int shortage = properties.shortageThreshold();

        List<AttendanceRecord> visible = ownRollNumber
                .map(roll -> records.findAll(AttendanceSpecifications.rollNumber(roll)))
                .orElseGet(records::findAll);

        if (visible.isEmpty()) {
            return DashboardSummary.empty(threshold, shortage);
        }

        long counted = visible.stream()
                .filter(r -> r.getAttendanceStatus().countsTowardsTotal())
                .count();
        long attended = visible.stream()
                .filter(r -> r.getAttendanceStatus().countsTowardsTotal())
                .filter(r -> r.getAttendanceStatus().countsAsAttended())
                .count();

        boolean studentView = ownRollNumber.isPresent();
        List<DashboardSummary.StudentStanding> standings = standings(visible, studentView);

        return new DashboardSummary(
                visible.size(),
                counted, attended, percent(attended, counted),
                threshold, shortage,
                subjectBreakdown(visible),
                standings.stream().filter(st -> st.percent() < threshold).toList(),
                standings.stream().filter(st -> st.percent() < shortage).toList(),
                studentView
                        ? cases.findByStudent_RollNumberOrderByOpenedAtDesc(ownRollNumber.get())
                               .stream().filter(c -> c.getStatus().isOpen()).count()
                        // Every open status, ESCALATED included. Listing
                        // them by hand here is what let ESCALATED be
                        // forgotten when it was added, so the filter is the
                        // enum's own definition of open.
                        : cases.countByStatusIn(Arrays.stream(CaseStatus.values())
                                .filter(CaseStatus::isOpen).toList()));
    }

    private List<DashboardSummary.SubjectBreakdown> subjectBreakdown(List<AttendanceRecord> visible) {
        Map<String, long[]> bySubject = new LinkedHashMap<>();
        visible.stream()
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
     * Every student's standing, for the at-risk and shortage lists.
     *
     * <p>Deliberately empty for a student's own dashboard: a student is
     * shown their own standing in the headline figure, and has no business
     * seeing a list of their classmates who are failing.
     */
    private List<DashboardSummary.StudentStanding> standings(List<AttendanceRecord> visible,
                                                             boolean studentView) {
        if (studentView) {
            return List.of();
        }

        record Tally(String name, long counted, long attended) {
        }
        Map<String, Tally> byStudent = new LinkedHashMap<>();
        for (AttendanceRecord r : visible) {
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
