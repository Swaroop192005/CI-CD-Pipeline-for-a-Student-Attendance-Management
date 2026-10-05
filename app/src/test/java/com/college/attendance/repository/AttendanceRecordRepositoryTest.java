package com.college.attendance.repository;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.Student;
import com.college.attendance.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence-level checks: default ordering (FR-08) and the uniqueness
 * constraint that enforces BR-01 in the database rather than only in a
 * service check.
 */
@DataJpaTest
@ActiveProfiles("test")
class AttendanceRecordRepositoryTest {

    @Autowired
    private AttendanceRecordRepository records;
    @Autowired
    private StudentRepository students;

    private Student student;

    @BeforeEach
    void setUp() {
        student = students.save(TestFixtures.student(TestFixtures.ROLL_1, "Aditya Rao"));
    }

    @Test
    @DisplayName("default list ordering is most recent session date first")
    void defaultSortIsRecentFirst() {
        records.save(record(LocalDate.now().minusDays(5), 1));
        records.save(record(LocalDate.now().minusDays(1), 1));
        records.save(record(LocalDate.now().minusDays(3), 1));

        Page<AttendanceRecord> page =
                records.findAllByOrderBySessionDateDescPeriodNumberAsc(PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(AttendanceRecord::getSessionDate)
                .containsExactly(
                        LocalDate.now().minusDays(1),
                        LocalDate.now().minusDays(3),
                        LocalDate.now().minusDays(5));
    }

    @Test
    @DisplayName("records on the same date are ordered by period ascending")
    void sameDateOrderedByPeriod() {
        LocalDate date = LocalDate.now().minusDays(2);
        records.save(record(date, 3));
        records.save(record(date, 1));
        records.save(record(date, 2));

        Page<AttendanceRecord> page =
                records.findAllByOrderBySessionDateDescPeriodNumberAsc(PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(AttendanceRecord::getPeriodNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("the database refuses a duplicate student/subject/date/period (BR-01)")
    void uniqueConstraintEnforcedByDatabase() {
        LocalDate date = LocalDate.now().minusDays(1);
        records.saveAndFlush(record(date, 1));

        assertThatThrownBy(() -> records.saveAndFlush(record(date, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existence check finds an already-recorded session")
    void existenceCheckMatchesTuple() {
        LocalDate date = LocalDate.now().minusDays(1);
        records.saveAndFlush(record(date, 4));

        assertThat(records.existsByStudent_RollNumberAndSubjectCodeAndSessionDateAndPeriodNumber(
                TestFixtures.ROLL_1, TestFixtures.SUBJECT, date, 4)).isTrue();
        assertThat(records.existsByStudent_RollNumberAndSubjectCodeAndSessionDateAndPeriodNumber(
                TestFixtures.ROLL_1, TestFixtures.SUBJECT, date, 5)).isFalse();
    }

    @Test
    @DisplayName("distinct subject codes are returned for filter drop-downs")
    void distinctSubjectCodes() {
        LocalDate date = LocalDate.now().minusDays(1);
        records.save(record(date, 1));
        records.save(new AttendanceRecord(student, "CS502", date, 2,
                AttendanceStatus.PRESENT, null, "faculty1"));
        records.save(new AttendanceRecord(student, "CS502", date, 3,
                AttendanceStatus.PRESENT, null, "faculty1"));

        assertThat(records.findDistinctSubjectCodes()).containsExactly("CS501", "CS502");
    }

    private AttendanceRecord record(LocalDate date, int period) {
        return new AttendanceRecord(student, TestFixtures.SUBJECT, date, period,
                AttendanceStatus.PRESENT, null, "faculty1");
    }
}
