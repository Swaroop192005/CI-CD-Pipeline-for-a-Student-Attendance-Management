package com.college.attendance.service;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.CaseStatus;
import com.college.attendance.domain.CaseTier;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.ShortageCase;
import com.college.attendance.domain.Student;
import com.college.attendance.repository.AppUserRepository;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.ShortageCaseRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Shortage cases, across both tiers.
 *
 * <p>Two things are easy to get wrong here and neither fails loudly. The
 * first is the band split: if the two tiers overlap, a student answers the
 * same question twice to two people who cannot see each other's case, which
 * is the queue the design set out to remove. The second is who may decide:
 * a URL rule cannot express "this case, in this band", so the tier check
 * lives in the service and has to be tested there.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShortageCaseServiceTest {

    private static final String SUBJECT = "CS501";

    @Autowired
    private ShortageCaseService cases;
    @Autowired
    private ShortageCaseRepository caseRepository;
    @Autowired
    private AttendanceRecordRepository records;
    @Autowired
    private StudentRepository students;
    @Autowired
    private AppUserRepository users;

    private Student critical;
    private Student middling;
    private Student healthy;

    @BeforeEach
    void reset() {
        caseRepository.deleteAllInBatch();
        records.deleteAllInBatch();
        students.deleteAllInBatch();
        users.deleteAllInBatch();

        critical = students.save(TestFixtures.student("1CS21CS010", "Critical Case"));
        middling = students.save(TestFixtures.student("1CS21CS011", "Middling Case"));
        healthy = students.save(TestFixtures.student("1CS21CS012", "Healthy Case"));

        users.save(TestFixtures.user("faculty1", Role.FACULTY, "{noop}x"));
        users.save(TestFixtures.user("hod1", Role.HOD, "{noop}x"));
        users.save(TestFixtures.studentUser("studentCritical", "{noop}x", critical.getRollNumber()));
        users.save(TestFixtures.studentUser("studentMiddling", "{noop}x", middling.getRollNumber()));

        // 20% -- below the 35% shortage threshold.
        attendance(critical, 2, 8);
        // 60% -- short of the 75% eligibility mark, above the shortage one.
        attendance(middling, 6, 4);
        // 100% -- nothing to answer for.
        attendance(healthy, 10, 0);

        TestFixtures.actAs("hod1", Role.HOD);
    }

    /** Writes {@code present} present and {@code absent} absent sessions. */
    private void attendance(Student student, int present, int absent) {
        int period = 1;
        for (int i = 0; i < present; i++, period++) {
            records.save(new AttendanceRecord(student, SUBJECT,
                    LocalDate.now().minusDays(period), period,
                    AttendanceStatus.PRESENT, null, "faculty1"));
        }
        for (int i = 0; i < absent; i++, period++) {
            records.save(new AttendanceRecord(student, SUBJECT,
                    LocalDate.now().minusDays(period), period,
                    AttendanceStatus.ABSENT, null, "faculty1"));
        }
    }

    // ---- bands ------------------------------------------------------------

    @Test
    @DisplayName("the percentage is computed the same way the dashboard computes it")
    void percentagesMatchTheSeededBands() {
        assertThat(cases.percentFor(critical.getRollNumber())).isEqualTo(20.0);
        assertThat(cases.percentFor(middling.getRollNumber())).isEqualTo(60.0);
        assertThat(cases.percentFor(healthy.getRollNumber())).isEqualTo(100.0);
    }

    @Test
    @DisplayName("the head of department's band is below the shortage threshold")
    void hodCandidatesAreTheCriticalBand() {
        assertThat(cases.candidates())
                .extracting(ShortageCaseService.CaseCandidate::rollNumber)
                .containsExactly(critical.getRollNumber());
    }

    @Test
    @DisplayName("faculty's band is short of eligibility but above the shortage threshold")
    void facultyCandidatesAreTheMiddleBand() {
        TestFixtures.actAs("faculty1", Role.FACULTY);

        assertThat(cases.candidates())
                .extracting(ShortageCaseService.CaseCandidate::rollNumber)
                .containsExactly(middling.getRollNumber());
    }

    @Test
    @DisplayName("the two bands do not overlap, so nobody is questioned twice")
    void bandsDoNotOverlap() {
        var hodBand = cases.candidates().stream()
                .map(ShortageCaseService.CaseCandidate::rollNumber).toList();
        TestFixtures.actAs("faculty1", Role.FACULTY);
        var facultyBand = cases.candidates().stream()
                .map(ShortageCaseService.CaseCandidate::rollNumber).toList();

        assertThat(hodBand).doesNotContainAnyElementsOf(facultyBand);
    }

    @Test
    @DisplayName("BR-12: faculty cannot raise a case that belongs to the head of department")
    void facultyRefusedTheCriticalBand() {
        TestFixtures.actAs("faculty1", Role.FACULTY);

        assertThatThrownBy(() -> cases.raise(critical.getRollNumber(), "Why?"))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("head of department");
    }

    @Test
    @DisplayName("BR-12: the head of department does not take faculty's band")
    void hodRefusedTheMiddleBand() {
        assertThatThrownBy(() -> cases.raise(middling.getRollNumber(), "Why?"))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("Faculty raise queries above that");
    }

    @Test
    @DisplayName("BR-12: nobody may raise a case against a student who is meeting the mark")
    void healthyStudentCannotBeQuestioned() {
        TestFixtures.actAs("faculty1", Role.FACULTY);

        assertThatThrownBy(() -> cases.raise(healthy.getRollNumber(), "Why?"))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("nothing to answer for");
    }

    @Test
    @DisplayName("BR-13: one open case per student, whichever tier raised it")
    void secondOpenCaseRefused() {
        cases.raise(critical.getRollNumber(), "Why?");

        assertThatThrownBy(() -> cases.raise(critical.getRollNumber(), "Again, why?"))
                .isInstanceOf(DuplicateCaseException.class);
    }

    @Test
    @DisplayName("a case needs an actual question")
    void blankQuestionRefused() {
        assertThatThrownBy(() -> cases.raise(critical.getRollNumber(), "   "))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("needs a question");
    }

    // ---- the conversation -------------------------------------------------

    @Test
    @DisplayName("BR-15: only the student a case is about may answer it")
    void onlyTheSubjectMayAnswer() {
        ShortageCase raised = cases.raise(critical.getRollNumber(), "Why?");
        TestFixtures.actAs("studentMiddling", Role.STUDENT);

        assertThatThrownBy(() -> cases.respond(raised.getId(), "Not my case"))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("not yours");
    }

    @Test
    @DisplayName("BR-14: a case cannot be decided before the student has answered")
    void cannotDecideBeforeAnAnswer() {
        ShortageCase raised = cases.raise(critical.getRollNumber(), "Why?");

        assertThatThrownBy(() -> cases.reject(raised.getId(), "No"))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("not answered yet");
    }

    @Test
    @DisplayName("BR-16: accepting marks the absences in the window EXCUSED")
    void acceptingExcusesTheWindow() {
        ShortageCase raised = cases.raise(critical.getRollNumber(), "Why?");
        TestFixtures.actAs("studentCritical", Role.STUDENT);
        cases.respond(raised.getId(), "I was in hospital.");
        TestFixtures.actAs("hod1", Role.HOD);

        ShortageCase decided = cases.accept(raised.getId(), "Medical note seen",
                LocalDate.now().minusDays(30), LocalDate.now());

        assertThat(decided.getStatus()).isEqualTo(CaseStatus.ACCEPTED);
        assertThat(decided.getExcusedCount()).isEqualTo(8);
        assertThat(records.findByStudent_RollNumberOrderBySessionDateAsc(critical.getRollNumber()))
                .filteredOn(r -> r.getAttendanceStatus() == AttendanceStatus.EXCUSED)
                .hasSize(8)
                .allSatisfy(r -> assertThat(r.getExcusedBy()).isEqualTo("hod1"));

        // BR-03: excused sessions leave both sides, so the figure improves
        // rather than the case merely closing.
        assertThat(cases.percentFor(critical.getRollNumber())).isEqualTo(100.0);
    }

    @Test
    @DisplayName("rejecting leaves the attendance untouched")
    void rejectingChangesNoRecords() {
        ShortageCase raised = cases.raise(critical.getRollNumber(), "Why?");
        TestFixtures.actAs("studentCritical", Role.STUDENT);
        cases.respond(raised.getId(), "No particular reason.");
        TestFixtures.actAs("hod1", Role.HOD);

        cases.reject(raised.getId(), "Not good enough");

        assertThat(cases.percentFor(critical.getRollNumber())).isEqualTo(20.0);
    }

    // ---- escalation -------------------------------------------------------

    private ShortageCase answeredFacultyCase() {
        TestFixtures.actAs("faculty1", Role.FACULTY);
        ShortageCase raised = cases.raise(middling.getRollNumber(), "Why the absences?");
        TestFixtures.actAs("studentMiddling", Role.STUDENT);
        cases.respond(raised.getId(), "I was busy.");
        TestFixtures.actAs("faculty1", Role.FACULTY);
        return cases.require(raised.getId());
    }

    @Test
    @DisplayName("a faculty case is raised in the faculty tier against the eligibility mark")
    void facultyCaseCarriesItsTier() {
        TestFixtures.actAs("faculty1", Role.FACULTY);

        ShortageCase raised = cases.raise(middling.getRollNumber(), "Why?");

        assertThat(raised.getTier()).isEqualTo(CaseTier.FACULTY);
        assertThat(raised.getThresholdAtOpen())
                .as("a faculty case is measured against eligibility, not shortage")
                .isEqualTo(75);
        assertThat(raised.getPercentAtOpen()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("faculty closing a case accepts the explanation but excuses nothing")
    void facultyCloseDoesNotExcuse() {
        ShortageCase answered = answeredFacultyCase();

        // No dates: a faculty case excuses nothing, so requiring a window
        // would ask for one that is never applied.
        ShortageCase closed = cases.accept(answered.getId(), "Fair enough", null, null);

        assertThat(closed.getStatus()).isEqualTo(CaseStatus.ACCEPTED);
        assertThat(closed.getExcusedCount()).isZero();
        assertThat(cases.percentFor(middling.getRollNumber()))
                .as("only the head of department can change the figure")
                .isEqualTo(60.0);
    }

    @Test
    @DisplayName("escalation hands the case over without re-asking the student")
    void escalationMovesTheTierAndKeepsTheReply() {
        ShortageCase answered = answeredFacultyCase();

        ShortageCase escalated = cases.escalate(answered.getId(), "No evidence offered.");

        assertThat(escalated.getStatus()).isEqualTo(CaseStatus.ESCALATED);
        assertThat(escalated.getTier()).isEqualTo(CaseTier.HOD);
        assertThat(escalated.getEscalatedBy()).isEqualTo("faculty1");
        assertThat(escalated.getEscalationNote()).isEqualTo("No evidence offered.");
        assertThat(escalated.getResponse())
                .as("the student's reply travels with the case")
                .isEqualTo("I was busy.");
        assertThat(escalated.wasEscalated()).isTrue();
    }

    @Test
    @DisplayName("an escalated case is still open, because nobody has ruled on it")
    void escalatedIsOpenNotDecided() {
        ShortageCase answered = answeredFacultyCase();

        ShortageCase escalated = cases.escalate(answered.getId(), "No evidence.");

        assertThat(escalated.getStatus().isOpen()).isTrue();
        assertThat(escalated.getStatus().isDecided()).isFalse();
        assertThat(escalated.getStatus().awaitsDecision()).isTrue();
    }

    @Test
    @DisplayName("escalating before the student has answered is refused")
    void cannotEscalateUnanswered() {
        TestFixtures.actAs("faculty1", Role.FACULTY);
        ShortageCase raised = cases.raise(middling.getRollNumber(), "Why?");

        assertThatThrownBy(() -> cases.escalate(raised.getId(), "Not satisfied"))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("not answered yet");
    }

    @Test
    @DisplayName("escalating requires a reason, which is what the head of department weighs")
    void escalationNeedsAReason() {
        ShortageCase answered = answeredFacultyCase();

        assertThatThrownBy(() -> cases.escalate(answered.getId(), "  "))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("why the explanation was not enough");
    }

    @Test
    @DisplayName("the head of department decides an escalated case, and may excuse")
    void hodDecidesAnEscalatedCase() {
        ShortageCase answered = answeredFacultyCase();
        cases.escalate(answered.getId(), "No evidence.");
        TestFixtures.actAs("hod1", Role.HOD);

        ShortageCase decided = cases.accept(answered.getId(), "Accepted on appeal",
                LocalDate.now().minusDays(30), LocalDate.now());

        assertThat(decided.getStatus()).isEqualTo(CaseStatus.ACCEPTED);
        assertThat(decided.getExcusedCount()).isEqualTo(4);
        assertThat(decided.getDecidedBy()).isEqualTo("hod1");
    }

    @Test
    @DisplayName("faculty cannot decide a case that has moved to the head of department")
    void facultyLosesAnEscalatedCase() {
        ShortageCase answered = answeredFacultyCase();
        cases.escalate(answered.getId(), "No evidence.");

        assertThatThrownBy(() -> cases.accept(answered.getId(), "changed my mind", null, null))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("Head of department");
    }

    @Test
    @DisplayName("the head of department cannot decide a case still in faculty's tier")
    void hodCannotReachIntoFacultysTier() {
        ShortageCase answered = answeredFacultyCase();
        TestFixtures.actAs("hod1", Role.HOD);

        // The URL rules let both roles POST a decision; only the row knows
        // which band it is in, so this refusal has to come from the service.
        assertThatThrownBy(() -> cases.reject(answered.getId(), "No"))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("Faculty query");
    }

    @Test
    @DisplayName("a case cannot be escalated twice")
    void cannotEscalateTwice() {
        ShortageCase answered = answeredFacultyCase();
        cases.escalate(answered.getId(), "No evidence.");

        assertThatThrownBy(() -> cases.escalate(answered.getId(), "Still no"))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("already belongs to the head of department");
    }

    @Test
    @DisplayName("staff see only their own tier's cases")
    void queuesAreSeparate() {
        TestFixtures.actAs("faculty1", Role.FACULTY);
        ShortageCase facultyCase = cases.raise(middling.getRollNumber(), "Why?");
        TestFixtures.actAs("hod1", Role.HOD);
        ShortageCase hodCase = cases.raise(critical.getRollNumber(), "Why?");

        assertThat(cases.mine()).extracting(ShortageCase::getId)
                .containsExactly(hodCase.getId());

        TestFixtures.actAs("faculty1", Role.FACULTY);
        assertThat(cases.mine()).extracting(ShortageCase::getId)
                .containsExactly(facultyCase.getId());
    }
}
