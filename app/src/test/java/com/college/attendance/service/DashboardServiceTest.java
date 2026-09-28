package com.college.attendance.service;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.Student;
import com.college.attendance.domain.WorkflowStatus;
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
 * <p>The two rules worth the most care are BR-04 (only approved records
 * are published as fact) and BR-03 (EXCUSED leaves both sides of the
 * fraction). Both are easy to get subtly wrong in a way that still
 * produces a plausible-looking number.
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

    private void record(Student student, String subject, int period, AttendanceStatus attendance,
                        WorkflowStatus workflow) {
        AttendanceRecord r = new AttendanceRecord(student, subject,
                LocalDate.now().minusDays(period), period, attendance, null, "faculty1");
        r.setWorkflowStatus(workflow);
        records.save(r);
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
    @DisplayName("FR-23: counts are reported per workflow status")
    void countsByWorkflowStatus() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT, WorkflowStatus.DRAFT);
        record(aditya, "CS501", 2, AttendanceStatus.PRESENT, WorkflowStatus.SUBMITTED);
        record(aditya, "CS501", 3, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 4, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 5, AttendanceStatus.PRESENT, WorkflowStatus.REJECTED);

        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.draftCount()).isEqualTo(1);
        assertThat(summary.submittedCount()).isEqualTo(1);
        assertThat(summary.approvedCount()).isEqualTo(2);
        assertThat(summary.rejectedCount()).isEqualTo(1);
        assertThat(summary.totalRecords()).isEqualTo(5);
    }

    @Test
    @DisplayName("BR-04: the percentage counts approved records only")
    void percentageUsesApprovedOnly() {
        // Approved: 1 present, 1 absent -> 50%
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 2, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);
        // Drafts that would skew the figure to 75% if wrongly included.
        record(aditya, "CS501", 3, AttendanceStatus.PRESENT, WorkflowStatus.DRAFT);
        record(aditya, "CS501", 4, AttendanceStatus.PRESENT, WorkflowStatus.SUBMITTED);

        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.sessionsCounted()).isEqualTo(2);
        assertThat(summary.sessionsAttended()).isEqualTo(1);
        assertThat(summary.attendancePercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("BR-03: LATE counts as attended")
    void lateCountsAsAttended() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 2, AttendanceStatus.LATE, WorkflowStatus.APPROVED);

        assertThat(dashboardService.summary().attendancePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("BR-03: EXCUSED leaves both the numerator and the denominator")
    void excusedIsExcludedFromBothSides() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 2, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 3, AttendanceStatus.EXCUSED, WorkflowStatus.APPROVED);

        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.sessionsCounted())
                .as("approved leave must not inflate the denominator")
                .isEqualTo(2);
        assertThat(summary.attendancePercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("FR-23: a per-subject breakdown is produced")
    void perSubjectBreakdown() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 2, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(aditya, "CS502", 3, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);
        record(aditya, "CS502", 4, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);

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
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 2, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 3, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 4, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);
        // Bhavana: 4 of 4 -> 100%, clear.
        for (int period = 1; period <= 4; period++) {
            record(bhavana, "CS501", period, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
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
        record(aditya, "CS501", 1, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 2, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);
        record(bhavana, "CS501", 1, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(bhavana, "CS501", 2, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);

        assertThat(dashboardService.summary().atRisk())
                .extracting(DashboardSummary.StudentStanding::percent)
                .containsExactly(0.0, 50.0);
    }

    @Test
    @DisplayName("FR-25: a student's dashboard counts only their own approved records")
    void studentDashboardIsScoped() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 2, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 3, AttendanceStatus.PRESENT, WorkflowStatus.DRAFT);
        for (int period = 1; period <= 4; period++) {
            record(bhavana, "CS501", period, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        }

        TestFixtures.actAs("student1", Role.STUDENT);
        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.totalRecords())
                .as("only the student's own approved records are shown")
                .isEqualTo(2);
        assertThat(summary.attendancePercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("a student is not shown the list of classmates who are failing")
    void studentDoesNotSeeAtRiskList() {
        record(aditya, "CS501", 1, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);
        record(bhavana, "CS501", 1, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);

        TestFixtures.actAs("student1", Role.STUDENT);

        assertThat(dashboardService.summary().atRisk()).isEmpty();
    }

    @Test
    @DisplayName("the eligibility verdict compares the overall percentage with the threshold")
    void eligibilityVerdict() {
        record(aditya, "CS501", 1, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 2, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 3, AttendanceStatus.PRESENT, WorkflowStatus.APPROVED);
        record(aditya, "CS501", 4, AttendanceStatus.ABSENT, WorkflowStatus.APPROVED);

        TestFixtures.actAs("student1", Role.STUDENT);
        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.attendancePercent()).isEqualTo(75.0);
        assertThat(summary.isMeetingThreshold())
                .as("exactly at the threshold is eligible, not below")
                .isTrue();
    }
}
