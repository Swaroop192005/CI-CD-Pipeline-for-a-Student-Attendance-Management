package com.college.attendance.service;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceSession;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.SessionStatus;
import com.college.attendance.repository.AppUserRepository;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.AttendanceSessionRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Taking attendance by session: faculty open, students mark themselves,
 * closing writes the rest as absent.
 *
 * <p>The rules that carry the weight here are the ones that decide what a
 * closed session <em>means</em>. If closing only recorded the students who
 * volunteered, a session would be a list of the conscientious rather than a
 * record of the class, and the percentage built on it would flatter
 * everybody who said nothing.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceSessionServiceTest {

    private static final String SUBJECT = "CS501";
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private AttendanceSessionService sessions;
    @Autowired
    private AttendanceSessionRepository sessionRepository;
    @Autowired
    private AttendanceRecordRepository records;
    @Autowired
    private StudentRepository students;
    @Autowired
    private AppUserRepository users;

    @BeforeEach
    void reset() {
        records.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
        students.deleteAllInBatch();
        users.deleteAllInBatch();

        students.save(TestFixtures.student(TestFixtures.ROLL_1, "Aditya Rao"));
        students.save(TestFixtures.student(TestFixtures.ROLL_2, "Bhavana Shetty"));
        users.save(TestFixtures.user("faculty1", Role.FACULTY, "{noop}x"));
        users.save(TestFixtures.studentUser("student1", "{noop}x", TestFixtures.ROLL_1));
        users.save(TestFixtures.studentUser("student2", "{noop}x", TestFixtures.ROLL_2));
        TestFixtures.actAs("faculty1", Role.FACULTY);
    }

    private AttendanceSession open(int period) {
        return sessions.open(SUBJECT, TODAY, period);
    }

    @Test
    @DisplayName("an opened session is OPEN and attributed to whoever opened it")
    void openingRecordsWhoOpenedIt() {
        AttendanceSession session = open(1);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(session.isOpen()).isTrue();
        assertThat(session.getOpenedBy()).isEqualTo("faculty1");
    }

    @Test
    @DisplayName("BR-09: a session cannot be opened for a future date")
    void futureSessionRefused() {
        assertThatThrownBy(() -> sessions.open(SUBJECT, TODAY.plusDays(1), 1))
                .isInstanceOf(SessionNotOpenException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("the same subject, date and period cannot be opened twice")
    void duplicateSessionRefused() {
        open(1);

        assertThatThrownBy(() -> open(1))
                .isInstanceOf(DuplicateSessionException.class)
                // The message must name the class, not a roll number: a
                // session clash has no student, and the old message left a
                // bare dash where one would go.
                .hasMessageContaining(SUBJECT)
                .hasMessageContaining("period 1")
                .extracting(Throwable::getMessage, org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .doesNotContain("for -");
    }

    @Test
    @DisplayName("a student marking themselves is recorded as present, by their own roll number")
    void studentMarksThemselves() {
        AttendanceSession session = open(1);
        TestFixtures.actAs("student1", Role.STUDENT);

        AttendanceRecord marked = sessions.markPresent(session.getId());

        assertThat(marked.getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(marked.getStudent().getRollNumber()).isEqualTo(TestFixtures.ROLL_1);
        assertThat(marked.getMarkedBy())
                .as("the student is the author of their own mark")
                .isEqualTo(TestFixtures.ROLL_1);
    }

    @Test
    @DisplayName("identity comes from the security context, never from the request")
    void facultyCannotMarkAsAStudent() {
        AttendanceSession session = open(1);

        // Still acting as faculty, who has no roll number. There is no
        // parameter to pass one, which is the point: a request cannot name
        // the student it marks.
        assertThatThrownBy(() -> sessions.markPresent(session.getId()))
                .isInstanceOf(NotPermittedException.class)
                .hasMessageContaining("student account");
    }

    @Test
    @DisplayName("BR-11: a student may mark a session only once")
    void markingTwiceRefused() {
        AttendanceSession session = open(1);
        TestFixtures.actAs("student1", Role.STUDENT);
        sessions.markPresent(session.getId());

        assertThatThrownBy(() -> sessions.markPresent(session.getId()))
                .isInstanceOf(DuplicateRecordException.class);
    }

    @Test
    @DisplayName("BR-09: a closed session cannot be marked")
    void closedSessionNotMarkable() {
        AttendanceSession session = open(1);
        sessions.close(session.getId());
        TestFixtures.actAs("student1", Role.STUDENT);

        assertThatThrownBy(() -> sessions.markPresent(session.getId()))
                .isInstanceOf(SessionNotOpenException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @DisplayName("BR-10: closing writes an absence for every student who did not mark")
    void closingWritesAbsences() {
        AttendanceSession session = open(1);
        TestFixtures.actAs("student1", Role.STUDENT);
        sessions.markPresent(session.getId());
        TestFixtures.actAs("faculty1", Role.FACULTY);

        AttendanceSession closed = sessions.close(session.getId());

        assertThat(closed.getStatus()).isEqualTo(SessionStatus.CLOSED);
        assertThat(closed.getMarkedCount()).isEqualTo(1);
        assertThat(closed.getAbsentCount())
                .as("the student who said nothing is absent, not missing")
                .isEqualTo(1);

        List<AttendanceRecord> written = records.findAll();
        assertThat(written).hasSize(2);
        assertThat(written)
                .filteredOn(r -> r.getStudent().getRollNumber().equals(TestFixtures.ROLL_2))
                .singleElement()
                .satisfies(r -> assertThat(r.getAttendanceStatus())
                        .isEqualTo(AttendanceStatus.ABSENT));
    }

    @Test
    @DisplayName("closing twice is refused, so absences cannot be written twice")
    void closingTwiceRefused() {
        AttendanceSession session = open(1);
        sessions.close(session.getId());

        assertThatThrownBy(() -> sessions.close(session.getId()))
                .isInstanceOf(SessionNotOpenException.class);
    }

    @Test
    @DisplayName("a struck student becomes absent, and the override names the faculty member")
    void strikeOverridesAMarkAndIsAttributed() {
        AttendanceSession session = open(1);
        TestFixtures.actAs("student1", Role.STUDENT);
        sessions.markPresent(session.getId());
        TestFixtures.actAs("faculty1", Role.FACULTY);

        sessions.strike(session.getId(), TestFixtures.ROLL_1, "Not in the room");

        assertThat(records.findAll())
                .filteredOn(r -> r.getStudent().getRollNumber().equals(TestFixtures.ROLL_1))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getAttendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
                    // No server-side check can prove presence, so the
                    // override has to be traceable to the person who made
                    // the judgement.
                    assertThat(r.getRemarks()).contains("Not in the room");
                    assertThat(r.getUpdatedBy()).isEqualTo("faculty1");
                });
    }

    @Test
    @DisplayName("a student cannot undo a strike by marking themselves again")
    void strikeCannotBeUndoneByTheStudent() {
        AttendanceSession session = open(1);
        TestFixtures.actAs("student1", Role.STUDENT);
        sessions.markPresent(session.getId());
        TestFixtures.actAs("faculty1", Role.FACULTY);
        sessions.strike(session.getId(), TestFixtures.ROLL_1, "Not in the room");

        TestFixtures.actAs("student1", Role.STUDENT);

        assertThatThrownBy(() -> sessions.markPresent(session.getId()))
                .isInstanceOf(DuplicateRecordException.class);
    }

    @Test
    @DisplayName("a student is only offered sessions they have not already marked")
    void awaitingExcludesWhatIsAlreadyMarked() {
        AttendanceSession first = open(1);
        open(2);
        TestFixtures.actAs("student1", Role.STUDENT);
        sessions.markPresent(first.getId());

        assertThat(sessions.openSessionsAwaiting(TestFixtures.ROLL_1))
                .singleElement()
                .satisfies(s -> assertThat(s.getPeriodNumber()).isEqualTo(2));
    }

    @Test
    @DisplayName("requireOpen refuses a closed session, so a prompt is never raised for one")
    void requireOpenRefusesClosed() {
        AttendanceSession session = open(1);
        sessions.close(session.getId());

        assertThatThrownBy(() -> sessions.requireOpen(session.getId()))
                .isInstanceOf(SessionNotOpenException.class);
    }
}
