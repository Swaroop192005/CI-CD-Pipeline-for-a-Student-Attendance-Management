package com.college.attendance.config;

import com.college.attendance.domain.AppUser;
import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.Student;
import com.college.attendance.repository.AppUserRepository;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Fixed seed: the generated attendance pattern must be reproducible. */
    private static final long RANDOM_SEED = 20_260_101L;

    private static final List<String> SUBJECTS = List.of("CS501", "CS502", "CS503");

    private final AppUserRepository users;
    private final StudentRepository students;
    private final AttendanceRecordRepository records;
    private final PasswordEncoder passwordEncoder;
    private final AttendanceProperties properties;

    public DataSeeder(AppUserRepository users, StudentRepository students,
                      AttendanceRecordRepository records, PasswordEncoder passwordEncoder,
                      AttendanceProperties properties) {
        this.users = users;
        this.students = students;
        this.records = records;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.seedData()) {
            log.info("Seeding disabled (attendance.seed-data=false)");
            return;
        }
        if (users.count() > 0) {
            log.info("Datastore already populated ({} users); skipping seed", users.count());
            return;
        }

        seedUsers();
        List<Student> roll = seedStudents();
        seedAttendance(roll);

        log.info("Seeded {} users, {} students, {} attendance records",
                users.count(), students.count(), records.count());
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
        users.save(new AppUser("student1", passwordEncoder.encode("Student@123"),
                "Aditya Rao", Role.STUDENT, "1CS21CS001"));
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
     * Ten sessions per subject over the preceding fortnight. Student 1 is
     * given a deliberately poor record for CS503 so that the at-risk list
     * on the dashboard has something real to show.
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

                for (Student student : roll) {
                    AttendanceStatus status = pickStatus(random, student, subject, session);
                    AttendanceRecord record = new AttendanceRecord(
                            student, subject, date, period, status, null, "faculty1");
                    records.save(record);
                }
            }
        }
    }

    private AttendanceStatus pickStatus(Random random, Student student, String subject, int session) {
        // Student 1 misses most CS503 sessions: gives the at-risk list a case.
        if ("1CS21CS001".equals(student.getRollNumber()) && "CS503".equals(subject)) {
            return session % 3 == 0 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
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
