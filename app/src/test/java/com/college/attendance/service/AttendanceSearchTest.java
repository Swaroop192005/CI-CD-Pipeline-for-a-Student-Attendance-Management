package com.college.attendance.service;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.WorkflowStatus;
import com.college.attendance.dto.AttendanceSearch;
import com.college.attendance.repository.AppUserRepository;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Search and filtering (US-04, FR-13 to FR-15) and the student scoping
 * that is AND-ed on top of it (US-09, FR-25).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceSearchTest {

    private static final LocalDate DAY_1 = LocalDate.now().minusDays(10);
    private static final LocalDate DAY_2 = LocalDate.now().minusDays(5);
    private static final LocalDate DAY_3 = LocalDate.now().minusDays(1);

    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private AttendanceRecordRepository records;
    @Autowired
    private StudentRepository students;
    @Autowired
    private AppUserRepository users;

    @BeforeEach
    void seed() {
        records.deleteAllInBatch();
        students.deleteAllInBatch();
        users.deleteAllInBatch();

        var s1 = students.save(TestFixtures.student(TestFixtures.ROLL_1, "Aditya Rao"));
        var s2 = students.save(TestFixtures.student(TestFixtures.ROLL_2, "Bhavana Shetty"));
        users.save(TestFixtures.user("faculty1", Role.FACULTY, "{noop}x"));
        users.save(TestFixtures.studentUser("student1", "{noop}x", TestFixtures.ROLL_1));

        records.save(new AttendanceRecord(s1, "CS501", DAY_1, 1, AttendanceStatus.PRESENT, null, "faculty1"));
        records.save(new AttendanceRecord(s1, "CS502", DAY_2, 2, AttendanceStatus.ABSENT, null, "faculty1"));
        records.save(new AttendanceRecord(s1, "CS501", DAY_3, 1, AttendanceStatus.PRESENT, null, "faculty1"));
        records.save(new AttendanceRecord(s2, "CS501", DAY_2, 1, AttendanceStatus.LATE, null, "faculty1"));
        records.save(new AttendanceRecord(s2, "CS502", DAY_3, 2, AttendanceStatus.PRESENT, null, "faculty1"));

        TestFixtures.actAs("faculty1", Role.FACULTY);
    }

    private AttendanceSearch search() {
        return new AttendanceSearch();
    }

    private java.util.List<AttendanceRecord> run(AttendanceSearch criteria) {
        return attendanceService.search(criteria, PageRequest.of(0, 50)).getContent();
    }

    @Test
    @DisplayName("AC-1: filtering by roll number returns only that student's records")
    void filterByRollNumber() {
        AttendanceSearch criteria = search();
        criteria.setRollNumber(TestFixtures.ROLL_1);

        assertThat(run(criteria)).hasSize(3)
                .allSatisfy(r -> assertThat(r.getStudent().getRollNumber())
                        .isEqualTo(TestFixtures.ROLL_1));
    }

    @Test
    @DisplayName("AC-2: filtering by subject returns only that subject")
    void filterBySubject() {
        AttendanceSearch criteria = search();
        criteria.setSubjectCode("CS502");

        assertThat(run(criteria)).hasSize(2)
                .allSatisfy(r -> assertThat(r.getSubjectCode()).isEqualTo("CS502"));
    }

    @Test
    @DisplayName("subject matching is case-insensitive")
    void subjectFilterIsCaseInsensitive() {
        AttendanceSearch criteria = search();
        criteria.setSubjectCode("cs502");

        assertThat(run(criteria)).hasSize(2);
    }

    @Test
    @DisplayName("AC-3: a date range includes both endpoints")
    void dateRangeInclusive() {
        AttendanceSearch criteria = search();
        criteria.setFrom(DAY_1);
        criteria.setTo(DAY_2);

        assertThat(run(criteria)).hasSize(3)
                .allSatisfy(r -> assertThat(r.getSessionDate())
                        .isBetween(DAY_1, DAY_2));
    }

    @Test
    @DisplayName("an open-ended lower bound still filters")
    void openEndedRange() {
        AttendanceSearch criteria = search();
        criteria.setFrom(DAY_3);

        assertThat(run(criteria)).hasSize(2);
    }

    @Test
    @DisplayName("filtering by attendance status")
    void filterByAttendanceStatus() {
        AttendanceSearch criteria = search();
        criteria.setAttendanceStatus(AttendanceStatus.PRESENT);

        assertThat(run(criteria)).hasSize(3);
    }

    @Test
    @DisplayName("filtering by workflow status")
    void filterByWorkflowStatus() {
        AttendanceSearch criteria = search();
        criteria.setWorkflowStatus(WorkflowStatus.DRAFT);

        assertThat(run(criteria)).hasSize(5);

        criteria.setWorkflowStatus(WorkflowStatus.APPROVED);
        assertThat(run(criteria)).isEmpty();
    }

    @Test
    @DisplayName("AC-4: filters combine with AND semantics")
    void filtersCombine() {
        AttendanceSearch criteria = search();
        criteria.setRollNumber(TestFixtures.ROLL_1);
        criteria.setSubjectCode("CS501");
        criteria.setAttendanceStatus(AttendanceStatus.PRESENT);

        assertThat(run(criteria)).hasSize(2)
                .allSatisfy(r -> {
                    assertThat(r.getStudent().getRollNumber()).isEqualTo(TestFixtures.ROLL_1);
                    assertThat(r.getSubjectCode()).isEqualTo("CS501");
                    assertThat(r.getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
                });
    }

    @Test
    @DisplayName("AC-5: a search matching nothing returns an empty page, not an error")
    void noMatchesReturnsEmpty() {
        AttendanceSearch criteria = search();
        criteria.setRollNumber("9XX99XX999");

        assertThat(run(criteria)).isEmpty();
    }

    @Test
    @DisplayName("blank filter values are treated as absent, not as a literal empty match")
    void blankFiltersIgnored() {
        AttendanceSearch criteria = search();
        criteria.setRollNumber("   ");
        criteria.setSubjectCode("");

        assertThat(criteria.isActive()).isFalse();
        assertThat(run(criteria)).hasSize(5);
    }

    @Test
    @DisplayName("results are ordered most recent session first regardless of filters")
    void resultsOrderedRecentFirst() {
        assertThat(run(search()))
                .extracting(AttendanceRecord::getSessionDate)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("FR-25: a student sees only their own approved records, whatever they filter by")
    void studentScopeCannotBeWidened() {
        // Approve one of student1's records and one of the other student's.
        var all = records.findAll();
        all.stream()
                .filter(r -> r.getStudent().getRollNumber().equals(TestFixtures.ROLL_1))
                .findFirst().ifPresent(r -> {
                    r.setWorkflowStatus(WorkflowStatus.APPROVED);
                    records.save(r);
                });
        all.stream()
                .filter(r -> r.getStudent().getRollNumber().equals(TestFixtures.ROLL_2))
                .findFirst().ifPresent(r -> {
                    r.setWorkflowStatus(WorkflowStatus.APPROVED);
                    records.save(r);
                });

        TestFixtures.actAs("student1", Role.STUDENT);

        // Deliberately ask for the other student's records and for drafts.
        AttendanceSearch criteria = search();
        criteria.setRollNumber(TestFixtures.ROLL_2);
        criteria.setWorkflowStatus(WorkflowStatus.DRAFT);

        assertThat(run(criteria))
                .as("a student's own filters must never widen their scope")
                .isEmpty();

        assertThat(run(search()))
                .hasSize(1)
                .allSatisfy(r -> {
                    assertThat(r.getStudent().getRollNumber()).isEqualTo(TestFixtures.ROLL_1);
                    assertThat(r.getWorkflowStatus()).isEqualTo(WorkflowStatus.APPROVED);
                });
    }

    @Test
    @DisplayName("FR-25: a student is refused a record that is not theirs")
    void studentCannotOpenAnothersRecord() {
        Long othersRecord = records.findAll().stream()
                .filter(r -> r.getStudent().getRollNumber().equals(TestFixtures.ROLL_2))
                .findFirst().orElseThrow().getId();

        TestFixtures.actAs("student1", Role.STUDENT);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> attendanceService.require(othersRecord))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("your own attendance records");
    }

    @Test
    @DisplayName("FR-25: a student is refused their own record while it is not yet approved")
    void studentCannotOpenOwnUnapprovedRecord() {
        Long ownDraft = records.findAll().stream()
                .filter(r -> r.getStudent().getRollNumber().equals(TestFixtures.ROLL_1))
                .filter(r -> r.getWorkflowStatus() == WorkflowStatus.DRAFT)
                .findFirst().orElseThrow().getId();

        TestFixtures.actAs("student1", Role.STUDENT);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> attendanceService.require(ownDraft))
                .isInstanceOf(NotPermittedException.class);
    }
}
