package com.college.attendance.service;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.Student;
import com.college.attendance.dto.AttendanceBucket;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Attendance grouped by week and by month.
 *
 * <p>Dates are fixed rather than relative to today. A test that said
 * "last week" would group differently depending on which day it ran, and a
 * bucketing bug that only appears when a week straddles a month boundary is
 * exactly the kind that a relative fixture hides.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceSummaryServiceTest {

    private static final String SUBJECT = "CS501";
    private static final String ROLL = "1CS21CS020";

    @Autowired
    private AttendanceSummaryService summaries;
    @Autowired
    private AttendanceRecordRepository records;
    @Autowired
    private StudentRepository students;

    private Student student;
    private int period = 1;

    @BeforeEach
    void reset() {
        records.deleteAllInBatch();
        students.deleteAllInBatch();
        student = students.save(TestFixtures.student(ROLL, "Bucket Subject"));
        period = 1;
    }

    private void on(LocalDate date, AttendanceStatus status) {
        records.save(new AttendanceRecord(student, SUBJECT, date, period++,
                status, null, "faculty1"));
    }

    @Test
    @DisplayName("a student with no records gets empty lists, not an error")
    void emptyIsEmpty() {
        assertThat(summaries.weekly(ROLL)).isEmpty();
        assertThat(summaries.monthly(ROLL)).isEmpty();
    }

    @Test
    @DisplayName("sessions in the same ISO week land in one bucket")
    void oneWeekOneBucket() {
        // Monday 5 to Friday 9 October 2026 is a single ISO week.
        on(LocalDate.of(2026, 10, 5), AttendanceStatus.PRESENT);
        on(LocalDate.of(2026, 10, 6), AttendanceStatus.PRESENT);
        on(LocalDate.of(2026, 10, 7), AttendanceStatus.ABSENT);
        on(LocalDate.of(2026, 10, 9), AttendanceStatus.PRESENT);

        assertThat(summaries.weekly(ROLL)).singleElement().satisfies(b -> {
            assertThat(b.attended()).isEqualTo(3);
            assertThat(b.counted()).isEqualTo(4);
            assertThat(b.percent()).isEqualTo(75);
            assertThat(b.from()).isEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(b.to()).isEqualTo(LocalDate.of(2026, 10, 9));
        });
    }

    @Test
    @DisplayName("a week straddling a month boundary stays one week")
    void weekAcrossAMonthBoundary() {
        // Monday 28 September to Thursday 1 October 2026: one ISO week,
        // two calendar months. Grouping on day-of-month arithmetic would
        // split this in two.
        on(LocalDate.of(2026, 9, 28), AttendanceStatus.PRESENT);
        on(LocalDate.of(2026, 9, 30), AttendanceStatus.PRESENT);
        on(LocalDate.of(2026, 10, 1), AttendanceStatus.ABSENT);

        List<AttendanceBucket> weekly = summaries.weekly(ROLL);
        List<AttendanceBucket> monthly = summaries.monthly(ROLL);

        assertThat(weekly).as("one ISO week").hasSize(1);
        assertThat(weekly.get(0).counted()).isEqualTo(3);
        assertThat(monthly).as("but two calendar months").hasSize(2);
        assertThat(monthly).extracting(AttendanceBucket::label)
                .containsExactly("September 2026", "October 2026");
    }

    @Test
    @DisplayName("a week straddling a year boundary stays one week")
    void weekAcrossAYearBoundary() {
        // Monday 28 December 2026 to Friday 1 January 2027 is ISO week 53
        // of the 2026 week-based year. Keying on the calendar year would
        // split it, and keying on week number alone would merge it with
        // week 53 of another year.
        on(LocalDate.of(2026, 12, 28), AttendanceStatus.PRESENT);
        on(LocalDate.of(2026, 12, 31), AttendanceStatus.ABSENT);
        on(LocalDate.of(2027, 1, 1), AttendanceStatus.PRESENT);

        assertThat(summaries.weekly(ROLL))
                .as("the ISO week-based year holds it together")
                .hasSize(1);
        assertThat(summaries.monthly(ROLL)).hasSize(2);
    }

    @Test
    @DisplayName("BR-03: an excused session counts on neither side, and is reported separately")
    void excusedLeavesBothSides() {
        on(LocalDate.of(2026, 10, 5), AttendanceStatus.PRESENT);
        on(LocalDate.of(2026, 10, 6), AttendanceStatus.ABSENT);
        on(LocalDate.of(2026, 10, 7), AttendanceStatus.EXCUSED);

        assertThat(summaries.weekly(ROLL)).singleElement().satisfies(b -> {
            assertThat(b.counted()).as("excused must not inflate the denominator").isEqualTo(2);
            assertThat(b.attended()).isEqualTo(1);
            assertThat(b.excused()).as("but it is still visible").isEqualTo(1);
            assertThat(b.percent()).isEqualTo(50);
        });
    }

    @Test
    @DisplayName("a period where everything was excused has no percentage, not zero")
    void allExcusedHasNoPercentage() {
        on(LocalDate.of(2026, 10, 5), AttendanceStatus.EXCUSED);
        on(LocalDate.of(2026, 10, 6), AttendanceStatus.EXCUSED);

        assertThat(summaries.weekly(ROLL)).singleElement().satisfies(b -> {
            assertThat(b.counted()).isZero();
            assertThat(b.excused()).isEqualTo(2);
            // Zero would read as "attended nothing", which is the opposite
            // of what an excused week says.
            assertThat(b.hasPercent()).isFalse();
            assertThat(b.percent()).isEqualTo(-1);
        });
    }

    @Test
    @DisplayName("BR-03: LATE counts as attended")
    void lateCountsAsAttended() {
        on(LocalDate.of(2026, 10, 5), AttendanceStatus.LATE);
        on(LocalDate.of(2026, 10, 6), AttendanceStatus.ABSENT);

        assertThat(summaries.weekly(ROLL)).singleElement()
                .satisfies(b -> assertThat(b.percent()).isEqualTo(50));
    }

    @Test
    @DisplayName("buckets come back oldest first, so a decline reads left to right")
    void bucketsAreChronological() {
        on(LocalDate.of(2026, 8, 3), AttendanceStatus.PRESENT);
        on(LocalDate.of(2026, 9, 7), AttendanceStatus.PRESENT);
        on(LocalDate.of(2026, 10, 5), AttendanceStatus.ABSENT);

        assertThat(summaries.monthly(ROLL)).extracting(AttendanceBucket::label)
                .containsExactly("August 2026", "September 2026", "October 2026");
    }

    @Test
    @DisplayName("one student's records never leak into another's buckets")
    void bucketsAreScopedToOneStudent() {
        Student other = students.save(TestFixtures.student("1CS21CS021", "Someone Else"));
        on(LocalDate.of(2026, 10, 5), AttendanceStatus.PRESENT);
        records.save(new AttendanceRecord(other, SUBJECT, LocalDate.of(2026, 10, 6), 99,
                AttendanceStatus.ABSENT, null, "faculty1"));

        assertThat(summaries.weekly(ROLL)).singleElement()
                .satisfies(b -> assertThat(b.counted()).isEqualTo(1));
        assertThat(summaries.weekly("1CS21CS021")).singleElement()
                .satisfies(b -> assertThat(b.counted()).isEqualTo(1));
    }

    @Test
    @DisplayName("the monthly total equals the weekly total, so the two views agree")
    void weeklyAndMonthlyAgree() {
        on(LocalDate.of(2026, 10, 5), AttendanceStatus.PRESENT);
        on(LocalDate.of(2026, 10, 6), AttendanceStatus.ABSENT);
        on(LocalDate.of(2026, 10, 13), AttendanceStatus.PRESENT);
        on(LocalDate.of(2026, 10, 20), AttendanceStatus.EXCUSED);

        long weeklyCounted = summaries.weekly(ROLL).stream()
                .mapToLong(AttendanceBucket::counted).sum();
        long monthlyCounted = summaries.monthly(ROLL).stream()
                .mapToLong(AttendanceBucket::counted).sum();

        // Two groupings of the same records must not disagree about how
        // many there were; a reader comparing the tables would have no way
        // to tell which one to believe.
        assertThat(weeklyCounted).isEqualTo(monthlyCounted).isEqualTo(3);
        assertThat(summaries.weekly(ROLL)).hasSize(3);
        assertThat(summaries.monthly(ROLL)).hasSize(1);
    }
}
