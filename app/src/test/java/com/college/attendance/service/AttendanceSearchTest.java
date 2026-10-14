package com.college.attendance.service;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.Role;
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
    @DisplayName("FR-25: a student sees only their own records, whatever they filter by")
    void studentScopeCannotBeWidened() {
        TestFixtures.actAs("student1", Role.STUDENT);

        // Deliberately ask for the other student's records.
        AttendanceSearch criteria = search();
        criteria.setRollNumber(TestFixtures.ROLL_2);

        assertThat(run(criteria))
                .as("a student's own filters must never widen their scope")
                .allSatisfy(r -> assertThat(r.getStudent().getRollNumber())
                        .isEqualTo(TestFixtures.ROLL_1));

        assertThat(run(search()))
                .isNotEmpty()
                .allSatisfy(r -> assertThat(r.getStudent().getRollNumber())
                        .isEqualTo(TestFixtures.ROLL_1));
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
    @DisplayName("a student may open their own record as soon as it exists")
    void studentCanOpenOwnRecord() {
        Long own = records.findAll().stream()
                .filter(r -> r.getStudent().getRollNumber().equals(TestFixtures.ROLL_1))
                .findFirst().orElseThrow().getId();

        TestFixtures.actAs("student1", Role.STUDENT);

        // Replaces a test asserting the opposite: a student used to be
        // refused their own record until somebody approved it. Nothing is
        // withheld now, which is the point of dropping per-record approval
        // -- a student can see what was recorded about them while they can
        // still remember whether it is right.
        assertThat(attendanceService.require(own).getStudent().getRollNumber())
                .isEqualTo(TestFixtures.ROLL_1);
    }
}
