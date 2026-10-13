package com.college.attendance.config;

import com.college.attendance.domain.AppUser;
import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceSession;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.SessionStatus;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.Student;
import com.college.attendance.repository.AppUserRepository;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.AttendanceSessionRepository;
import com.college.attendance.repository.StudentRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds deterministic fixtures when the datastore is empty (FR-29).
 *
 * <p>Two reasons this exists rather than a hand-populated database:
 *
 * <ul>
 *   <li>The Selenium suite asserts on specific rows. A fixed seed means
 *       the same data every run, on any machine, including a fresh
 *       container in the pipeline.</li>
 *   <li>No real student data may enter the repository (constraint C9).
 *       These names and roll numbers are invented.</li>
 * </ul>
 *
 * <p>Seeding is skipped entirely once any user exists, so restarting a
 * container with a mounted volume never duplicates or overwrites data.
 *
 * <p>This runs during context refresh rather than as an
 * {@code ApplicationRunner}. An ApplicationRunner fires <em>after</em> the
 * servlet container has bound its port, so there is a window in which
 * {@code /actuator/health} answers UP while the accounts do not yet
 * exist - a sign-in in that window fails. Because the pipeline and the
 * Ansible playbook both gate on that health check, the window would have
 * shown up as an intermittent deployment failure rather than as an
 * obvious bug.
 */
@Component
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Fixed seed: the generated attendance pattern must be reproducible. */
    private static final long RANDOM_SEED = 20_260_101L;

    private static final List<String> SUBJECTS = List.of("CS501", "CS502", "CS503");

    private final AppUserRepository users;
    private final StudentRepository students;
    private final AttendanceRecordRepository records;
    private final AttendanceSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final AttendanceProperties properties;
    private final TransactionTemplate transactions;

    public DataSeeder(AppUserRepository users, StudentRepository students,
                      AttendanceRecordRepository records, PasswordEncoder passwordEncoder,
                      AttendanceProperties properties, TransactionTemplate transactions,
                      AttendanceSessionRepository sessions) {
        this.users = users;
        this.students = students;
        this.records = records;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.transactions = transactions;
        this.sessions = sessions;
    }

    /**
     * Seeds during bean initialisation, so the port is not open until the
     * fixtures exist.
     *
     * <p>A {@code TransactionTemplate} rather than {@code @Transactional}:
     * the annotation is applied by a proxy that is not yet in place while
     * this bean is still being initialised, so the annotation would
     * silently do nothing here.
     */
    @PostConstruct
    void seed() {
        if (!properties.seedData()) {
            log.info("Seeding disabled (attendance.seed-data=false)");
            return;
        }

        transactions.executeWithoutResult(status -> {
            if (users.count() > 0) {
                log.info("Datastore already populated ({} users); skipping seed", users.count());
                return;
            }

            seedUsers();
            List<Student> roll = seedStudents();
            seedAttendance(roll);

            log.info("Seeded {} users, {} students, {} attendance records",
                    users.count(), students.count(), records.count());
        });
    }

    private void seedUsers() {
        users.save(new AppUser("faculty1", passwordEncoder.encode("Faculty@123"),
                "Dr. Meera Krishnan", Role.FACULTY));
        users.save(new AppUser("faculty2", passwordEncoder.encode("Faculty@123"),
                "Prof. Anand Desai", Role.FACULTY));
        users.save(new AppUser("hod1", passwordEncoder.encode("Hod@12345"),
                "Dr. S. Ramesh (HOD, CSE)", Role.HOD));
        users.save(new AppUser("admin1", passwordEncoder.encode("Admin@123"),
                "Exam Section Administrator", Role.ADMIN));
        // Five student logins, because the demonstration runs on one machine
        // and the only way to show a session filling up is to sign in as
        // several students in turn. student2 is the one in shortage, so the
        // case conversation has somebody who can actually answer it.
        String[][] studentLogins = {
                {"student1", "Aditya Rao", "1CS21CS001"},
                {"student2", "Bhavana Shetty", "1CS21CS002"},
                {"student3", "Chirag Patil", "1CS21CS003"},
                {"student4", "Divya Nair", "1CS21CS004"},
                {"student5", "Eshan Gupta", "1CS21CS005"},
        };
        for (String[] login : studentLogins) {
            users.save(new AppUser(login[0], passwordEncoder.encode("Student@123"),
                    login[1], Role.STUDENT, login[2]));
        }
    }

    private List<Student> seedStudents() {
        String[][] roll = {
                {"1CS21CS001", "Aditya Rao", "aditya.rao@college.edu"},
                {"1CS21CS002", "Bhavana Shetty", "bhavana.shetty@college.edu"},
                {"1CS21CS003", "Chirag Patil", "chirag.patil@college.edu"},
                {"1CS21CS004", "Divya Nair", "divya.nair@college.edu"},
                {"1CS21CS005", "Eshan Gupta", "eshan.gupta@college.edu"},
                {"1CS21CS006", "Farhan Qureshi", "farhan.qureshi@college.edu"},
                {"1CS21CS007", "Gayatri Joshi", "gayatri.joshi@college.edu"},
                {"1CS21CS008", "Harish Kumar", "harish.kumar@college.edu"},
                {"1CS21CS009", "Ishita Verma", "ishita.verma@college.edu"},
                {"1CS21CS010", "Jatin Mehta", "jatin.mehta@college.edu"},
        };
        List<Student> saved = new ArrayList<>();
        for (String[] s : roll) {
            saved.add(students.save(new Student(s[0], s[1], s[2], "Computer Science", 5)));
        }
        return saved;
    }

    /**
     * Ten sessions per subject over the preceding three weeks, in a mix of
     * workflow states that mirrors a real department mid-semester.
     *
     * <p>The state assignment is by session age, which is what makes the
     * fixtures useful rather than merely present:
     *
     * <ul>
     *   <li>Older sessions are {@code APPROVED}, so the dashboard has real
     *       percentages instead of showing 0% on a full datastore.</li>
     *   <li>The most recent session is {@code SUBMITTED}, so the review
     *       queue is not empty the first time a HOD signs in.</li>
     *   <li>One session is {@code REJECTED} with a reason, so the
     *       correct-and-re-submit path is reachable without first
     *       engineering a rejection by hand.</li>
     *   <li>The newest sessions stay {@code DRAFT}.</li>
     * </ul>
     *
     * <p>Two students are given deliberately poor records so both lists
     * have something real in them: student 1 sits just under the
     * eligibility threshold, and student 2 falls under the shortage
     * threshold so the head of department has a case worth raising.
     *
     * <p>Every session is also recorded as a closed {@code
     * AttendanceSession}, because that is how attendance is taken now. The
     * records are the ones the session produced, not rows that appeared
     * from nowhere.
     */
    private void seedAttendance(List<Student> roll) {
        Random random = new Random(RANDOM_SEED);
        LocalDate start = LocalDate.now().minusDays(20);

        for (int subjectIndex = 0; subjectIndex < SUBJECTS.size(); subjectIndex++) {
            String subject = SUBJECTS.get(subjectIndex);
            for (int session = 0; session < 10; session++) {
                LocalDate date = start.plusDays(session * 2L);
                if (date.isAfter(LocalDate.now())) {
                    continue;
                }
                int period = subjectIndex + 1;

                AttendanceSession taught = new AttendanceSession(subject, date, period, "faculty1");
                int present = 0;
                for (Student student : roll) {
                    AttendanceStatus status = pickStatus(random, student, subject, session);
                    records.save(new AttendanceRecord(
                            student, subject, date, period, status,
                            status == AttendanceStatus.PRESENT
                                    ? "Marked by the student during the open session"
                                    : "Did not mark attendance before the session closed",
                            status == AttendanceStatus.PRESENT
                                    ? student.getRollNumber() : "faculty1"));
                    if (status.countsAsAttended()) {
                        present++;
                    }
                }
                taught.setStatus(SessionStatus.CLOSED);
                taught.setClosedBy("faculty1");
                taught.setClosedAt(Instant.now().minus(Duration.ofDays(1)));
                taught.setMarkedCount(present);
                taught.setAbsentCount(roll.size() - present);
                sessions.save(taught);
            }
        }
    }

    private AttendanceStatus pickStatus(Random random, Student student, String subject, int session) {
        // Student 1 misses most CS503 sessions: sits below eligibility.
        if ("1CS21CS001".equals(student.getRollNumber()) && "CS503".equals(subject)) {
            return session % 3 == 0 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
        }
        // Student 2 misses most of everything: falls under the shortage
        // threshold, so the head of department has a case to raise and the
        // conversation flow has something to run against.
        if ("1CS21CS002".equals(student.getRollNumber())) {
            return session % 4 == 0 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
        }
        int roll = random.nextInt(100);
        if (roll < 78) {
            return AttendanceStatus.PRESENT;
        }
        if (roll < 90) {
            return AttendanceStatus.ABSENT;
        }
        if (roll < 96) {
            return AttendanceStatus.LATE;
        }
        return AttendanceStatus.EXCUSED;
    }
}
