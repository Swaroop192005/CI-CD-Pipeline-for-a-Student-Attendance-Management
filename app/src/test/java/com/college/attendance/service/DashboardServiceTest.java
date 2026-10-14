package com.college.attendance.service;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.Student;
import com.college.attendance.dto.DashboardSummary;
import com.college.attendance.repository.AppUserRepository;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashboard aggregation (US-08, US-09, US-20; FR-23, FR-24).
 *
 * <p>The rule worth the most care is BR-03: EXCUSED leaves both sides of
 * the fraction. It is easy to get subtly wrong in a way that still produces
 * a plausible-looking number.
 *
 * <p>BR-04 used to sit beside it -- only approved records counted as fact.
 * Per-record approval is gone, so every record now counts as soon as it is
 * written, and the tests that distinguished draft from approved went with
 * it. What replaced that safeguard is the shortage case: the figure is
 * trusted, and a student with a bad one is asked about it.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private AttendanceRecordRepository records;
    @Autowired
    private StudentRepository students;
    @Autowired
    private AppUserRepository users;

    private Student aditya;
    private Student bhavana;

    @BeforeEach
    void reset() {
        records.deleteAllInBatch();
        students.deleteAllInBatch();
        users.deleteAllInBatch();
        aditya = students.save(TestFixtures.student(TestFixtures.ROLL_1, "Aditya Rao"));
        bhavana = students.save(TestFixtures.student(TestFixtures.ROLL_2, "Bhavana Shetty"));
        users.save(TestFixtures.user("hod1", Role.HOD, "{noop}x"));
        users.save(TestFixtures.studentUser("student1", "{noop}x", TestFixtures.ROLL_1));
        TestFixtures.actAs("hod1", Role.HOD);
    }

    private void record(Student student, String subject, int period, AttendanceStatus attendance) {
        records.save(new AttendanceRecord(student, subject,
                LocalDate.now().minusDays(period), period, attendance, null, "faculty1"));
    }

    @Test
    @DisplayName("FR-24: an empty datastore renders zeroes, not an error")
    void emptyStateIsZero() {
        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.totalRecords()).isZero();
        assertThat(summary.attendancePercent()).isZero();
        assertThat(summary.subjects()).isEmpty();
        assertThat(summary.atRisk()).isEmpty();
        assertThat(summary.eligibilityThreshold()).isEqualTo(75);
    }

    @Test
    @DisplayName("FR-23: every record counts as soon as it is written")
    void everyRecordCounts() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 2, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 3, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 4, AttendanceStatus.ABSENT);
        record(aditya, "CS501", 5, AttendanceStatus.PRESENT);

        DashboardSummary summary = dashboardService.summary();

        // Replaces a test that asserted draft/submitted/approved/rejected
        // counts. There is no pending state to hold a record back any more,
        // so five records written means five records counted, and nothing
        // sits invisible waiting for somebody to approve it.
        assertThat(summary.totalRecords()).isEqualTo(5);
        assertThat(summary.sessionsCounted()).isEqualTo(5);
        assertThat(summary.sessionsAttended()).isEqualTo(4);
        assertThat(summary.attendancePercent()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("the percentage is attended over counted, with nothing held back")
    void percentageIsAttendedOverCounted() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 2, AttendanceStatus.ABSENT);

        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.sessionsCounted()).isEqualTo(2);
        assertThat(summary.sessionsAttended()).isEqualTo(1);
        assertThat(summary.attendancePercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("BR-03: LATE counts as attended")
    void lateCountsAsAttended() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 2, AttendanceStatus.LATE);

        assertThat(dashboardService.summary().attendancePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("BR-03: EXCUSED leaves both the numerator and the denominator")
    void excusedIsExcludedFromBothSides() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 2, AttendanceStatus.ABSENT);
        record(aditya, "CS501", 3, AttendanceStatus.EXCUSED);

        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.sessionsCounted())
                .as("excused leave must not inflate the denominator")
                .isEqualTo(2);
        assertThat(summary.attendancePercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("FR-23: a per-subject breakdown is produced")
    void perSubjectBreakdown() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 2, AttendanceStatus.PRESENT);
        record(aditya, "CS502", 3, AttendanceStatus.ABSENT);
        record(aditya, "CS502", 4, AttendanceStatus.PRESENT);

        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.subjects()).hasSize(2);
        assertThat(summary.subjects())
                .filteredOn(s -> s.subjectCode().equals("CS501"))
                .singleElement()
                .satisfies(s -> assertThat(s.percent()).isEqualTo(100.0));
        assertThat(summary.subjects())
                .filteredOn(s -> s.subjectCode().equals("CS502"))
                .singleElement()
                .satisfies(s -> assertThat(s.percent()).isEqualTo(50.0));
    }

    @Test
    @DisplayName("US-20: students below the threshold appear on the at-risk list")
    void atRiskBelowThreshold() {
        // Aditya: 1 of 4 approved -> 25%, below the 75% threshold.
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 2, AttendanceStatus.ABSENT);
        record(aditya, "CS501", 3, AttendanceStatus.ABSENT);
        record(aditya, "CS501", 4, AttendanceStatus.ABSENT);
        // Bhavana: 4 of 4 -> 100%, clear.
        for (int period = 1; period <= 4; period++) {
            record(bhavana, "CS501", period, AttendanceStatus.PRESENT);
        }

        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.atRisk()).singleElement().satisfies(s -> {
            assertThat(s.rollNumber()).isEqualTo(TestFixtures.ROLL_1);
            assertThat(s.percent()).isEqualTo(25.0);
        });
    }

    @Test
    @DisplayName("the at-risk list is ordered worst first, so the urgent case is at the top")
    void atRiskOrderedWorstFirst() {
        record(aditya, "CS501", 1, AttendanceStatus.ABSENT);
        record(aditya, "CS501", 2, AttendanceStatus.ABSENT);
        record(bhavana, "CS501", 1, AttendanceStatus.PRESENT);
        record(bhavana, "CS501", 2, AttendanceStatus.ABSENT);

        assertThat(dashboardService.summary().atRisk())
                .extracting(DashboardSummary.StudentStanding::percent)
                .containsExactly(0.0, 50.0);
    }

    @Test
    @DisplayName("FR-25: a student's dashboard counts only their own records")
    void studentDashboardIsScoped() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 2, AttendanceStatus.ABSENT);
        record(aditya, "CS501", 3, AttendanceStatus.PRESENT);
        for (int period = 1; period <= 4; period++) {
            record(bhavana, "CS501", period, AttendanceStatus.PRESENT);
        }

        TestFixtures.actAs("student1", Role.STUDENT);
        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.totalRecords())
                .as("only the student's own records are shown")
                .isEqualTo(3);
        assertThat(summary.attendancePercent())
                .as("2 of 3 attended")
                .isEqualTo(66.7);
    }

    @Test
    @DisplayName("a student is not shown the list of classmates who are failing")
    void studentDoesNotSeeAtRiskList() {
        record(aditya, "CS501", 1, AttendanceStatus.ABSENT);
        record(bhavana, "CS501", 1, AttendanceStatus.ABSENT);

        TestFixtures.actAs("student1", Role.STUDENT);

        assertThat(dashboardService.summary().atRisk()).isEmpty();
    }

    @Test
    @DisplayName("the eligibility verdict compares the overall percentage with the threshold")
    void eligibilityVerdict() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 2, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 3, AttendanceStatus.PRESENT);
        record(aditya, "CS501", 4, AttendanceStatus.ABSENT);

        TestFixtures.actAs("student1", Role.STUDENT);
        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.attendancePercent()).isEqualTo(75.0);
        assertThat(summary.isMeetingThreshold())
                .as("exactly at the threshold is eligible, not below")
                .isTrue();
    }
}
