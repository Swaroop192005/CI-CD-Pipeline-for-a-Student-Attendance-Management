package com.college.attendance.e2e;

import com.college.attendance.e2e.pages.AttendanceListPage;
import com.college.attendance.e2e.support.BaseJourney;
import com.college.attendance.e2e.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey J2 — search, filter, paginate and the empty state.
 *
 * <p>Covers US-04 (FR-13 … FR-15) and the pagination part of US-02. This
 * is the journey that replaces "read the register until you find the
 * student", so the assertions are about the result set actually narrowing,
 * not merely about the page loading.
 */
class J2SearchAndFilterIT extends BaseJourney {

    @Test
    @DisplayName("J2: filtering by roll number returns only that student's records")
    void filterByRollNumber() {
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);

        AttendanceListPage list = new AttendanceListPage(driver(), baseUrl).open();
        int unfiltered = list.resultCount();
        assertThat(unfiltered).as("the fixtures must provide something to filter").isGreaterThan(20);

        list.filterByRollNumber(TestData.STUDENT_ROLL).applyFilters();

        assertThat(list.resultCount()).isLessThan(unfiltered);
        assertThat(list.filtersAreActive()).isTrue();
        assertThat(list.rollNumbersOnPage())
                .isNotEmpty()
                .containsOnly(TestData.STUDENT_ROLL);
    }

    @Test
    @DisplayName("J2b: filters combine with AND, and the result set narrows at each step")
    void filtersCombineWithAnd() {
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);

        AttendanceListPage list = new AttendanceListPage(driver(), baseUrl).open();
        int all = list.resultCount();

        list.filterBySubject(TestData.SUBJECT_C).applyFilters();
        int bySubject = list.resultCount();
        assertThat(bySubject).isLessThan(all);
        assertThat(list.subjectsOnPage()).containsOnly(TestData.SUBJECT_C);

        list.filterByRollNumber(TestData.STUDENT_ROLL).applyFilters();
        int bySubjectAndRoll = list.resultCount();
        assertThat(bySubjectAndRoll).isLessThanOrEqualTo(bySubject);
        assertThat(list.subjectsOnPage()).containsOnly(TestData.SUBJECT_C);
        assertThat(list.rollNumbersOnPage()).containsOnly(TestData.STUDENT_ROLL);

        list.filterByAttendanceStatus("ABSENT").applyFilters();
        assertThat(list.attendanceStatusesOnPage()).containsOnly("Absent");
        assertThat(list.resultCount()).isLessThanOrEqualTo(bySubjectAndRoll);
    }

    @Test
    @DisplayName("J2c: a search matching nothing shows an explicit empty state, not a blank table")
    void emptySearchShowsEmptyState() {
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);

        AttendanceListPage list = new AttendanceListPage(driver(), baseUrl).open();
        list.filterByRollNumber("9XX99XX999").applyFilters();

        assertThat(list.resultCount()).isZero();
        assertThat(list.showsEmptyState()).isTrue();
        assertThat(list.emptyStateText())
                .as("the empty state must tell the user what to do next")
                .containsIgnoringCase("no records match");
        assertThat(list.rowsOnPage()).isZero();
    }

    @Test
    @DisplayName("J2d: results paginate at ten rows and filters survive the page change (FR-14)")
    void filtersSurvivePagination() {
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);

        AttendanceListPage list = new AttendanceListPage(driver(), baseUrl).open();
        list.filterBySubject(TestData.SUBJECT_A).applyFilters();

        assertThat(list.rowsOnPage()).isEqualTo(10);
        assertThat(list.hasPagination()).isTrue();
        assertThat(list.pageInfo()).contains("Page 1");
        assertThat(list.subjectsOnPage()).containsOnly(TestData.SUBJECT_A);

        list.nextPage();

        assertThat(list.pageInfo()).contains("Page 2");
        assertThat(list.subjectsOnPage())
                .as("the subject filter must still apply on page 2")
                .containsOnly(TestData.SUBJECT_A);
        assertThat(list.filtersAreActive()).isTrue();
    }

    @Test
    @DisplayName("J2e: clearing the filters restores the full result set")
    void clearingFiltersRestoresAll() {
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);

        AttendanceListPage list = new AttendanceListPage(driver(), baseUrl).open();
        int all = list.resultCount();

        list.filterBySubject(TestData.SUBJECT_B).applyFilters();
        assertThat(list.resultCount()).isLessThan(all);

        list.clearFilters();
        assertThat(list.resultCount()).isEqualTo(all);
        assertThat(list.filtersAreActive()).isFalse();
    }
}
