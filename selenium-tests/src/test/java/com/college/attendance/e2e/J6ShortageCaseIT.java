package com.college.attendance.e2e;

import com.college.attendance.e2e.pages.CasePage;
import com.college.attendance.e2e.support.BaseJourney;
import com.college.attendance.e2e.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey J6 — a shortage conversation, from faculty query to the head of
 * department's ruling.
 *
 * <p>The candidate is read from the page rather than hard-coded. Earlier
 * journeys write attendance, which moves percentages and therefore moves
 * students between bands: a fixed roll number here would pass or fail
 * depending on what ran before it, which is the sort of order dependence
 * that makes a browser suite untrustworthy.
 */
class J6ShortageCaseIT extends BaseJourney {

    /** student1..student5 speak for 1CS21CS001..005. */
    private static String loginFor(String rollNumber) {
        String digits = rollNumber.replaceAll(".*?(\\d+)$", "$1");
        return "student" + Integer.parseInt(digits);
    }

    @Test
    @DisplayName("J6: faculty ask, the student answers, faculty escalate, the head of department rules")
    void facultyQueryEscalatesToTheHeadOfDepartment() {
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);
        CasePage cases = new CasePage(driver(), baseUrl).openList();

        if (!cases.hasCandidates()) {
            System.out.println("[J6] no student is in faculty's band on this dataset; "
                    + "nothing to question, so the journey has nothing to assert");
            return;
        }

        String roll = cases.candidateRollNumbers().get(0);
        String studentLogin = loginFor(roll);

        cases.raiseAgainstFirst("Why have you been missing these classes?");
        long id = cases.caseId();
        assertThat(cases.tier()).isEqualTo("Faculty query");
        assertThat(cases.status()).isEqualTo("AWAITING_RESPONSE");

        // The student answers in their own login.
        switchUser(studentLogin, TestData.STUDENT_PASS);
        CasePage asStudent = new CasePage(driver(), baseUrl).open(id);
        assertThat(asStudent.question()).containsIgnoringCase("missing these classes");
        assertThat(asStudent.canRespond()).isTrue();
        asStudent.respond("I was unwell for a fortnight.");
        assertThat(asStudent.status()).isEqualTo("ANSWERED");

        // The head of department must not be able to decide faculty's case.
        switchUser(TestData.HOD_USER, TestData.HOD_PASS);
        CasePage asHod = new CasePage(driver(), baseUrl).open(id);
        assertThat(asHod.canAccept())
                .as("a case in faculty's band is not the head of department's to decide")
                .isFalse();

        // Faculty are not satisfied and pass it up.
        switchUser(TestData.FACULTY_USER, TestData.FACULTY_PASS);
        CasePage asFaculty = new CasePage(driver(), baseUrl).open(id);
        assertThat(asFaculty.response()).containsIgnoringCase("unwell");
        assertThat(asFaculty.canEscalate()).isTrue();
        asFaculty.escalate("No evidence offered, and the absences continued afterwards.");

        assertThat(asFaculty.status()).isEqualTo("ESCALATED");
        assertThat(asFaculty.showsEscalationTrail()).isTrue();
        assertThat(asFaculty.canAccept())
                .as("faculty give up the decision when they escalate")
                .isFalse();

        // The student is not asked a second time.
        switchUser(studentLogin, TestData.STUDENT_PASS);
        assertThat(new CasePage(driver(), baseUrl).open(id).canRespond())
                .as("escalation must not make the student answer the same absence twice")
                .isFalse();

        // The head of department rules on the conversation as it stands.
        switchUser(TestData.HOD_USER, TestData.HOD_PASS);
        CasePage deciding = new CasePage(driver(), baseUrl).open(id);
        assertThat(deciding.response()).containsIgnoringCase("unwell");
        assertThat(deciding.escalationNote()).containsIgnoringCase("no evidence");
        assertThat(deciding.canAccept()).isTrue();
        assertThat(deciding.canReject()).isTrue();

        // A deliberately empty window. Accepting with a year-wide one
        // converted every one of this student's absences to EXCUSED, which
        // silently broke J2's filter assertions -- a journey that destroys
        // the fixtures another journey reads is worse than no journey at
        // all, because the failure surfaces somewhere unrelated. What the
        // excuse window actually does to records is covered precisely, and
        // in isolation, by ShortageCaseServiceTest.
        deciding.accept("Accepted on appeal", "1900-01-01", "1900-01-02");
        assertThat(deciding.status()).isEqualTo("ACCEPTED");
        assertThat(deciding.successMessage()).containsIgnoringCase("accepted");

        System.out.println("[J6] one conversation carried a student from a faculty query to a "
                + "decision without anybody clearing a queue");
    }
}
