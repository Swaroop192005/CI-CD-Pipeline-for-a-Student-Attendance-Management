package com.college.attendance.e2e.support;

/**
 * The seeded fixtures the journeys assert against.
 *
 * <p>Centralised so that a change to the seed data breaks compilation in
 * one place rather than producing six confusing assertion failures, and so
 * that the test data is documented rather than scattered as string
 * literals.
 *
 * <p>These are invented names and roll numbers. No real student data is
 * used anywhere in this project (constraint C9).
 */
public final class TestData {

    private TestData() {
    }

    // ---- Accounts ---------------------------------------------------------

    public static final String FACULTY_USER = "faculty1";
    public static final String FACULTY_PASS = "Faculty@123";

    public static final String OTHER_FACULTY_USER = "faculty2";
    public static final String OTHER_FACULTY_PASS = "Faculty@123";

    public static final String HOD_USER = "hod1";
    public static final String HOD_PASS = "Hod@12345";

    public static final String ADMIN_USER = "admin1";
    public static final String ADMIN_PASS = "Admin@123";

    public static final String STUDENT_USER = "student1";
    public static final String STUDENT_PASS = "Student@123";

    /** The roll number the student account speaks for. */
    public static final String STUDENT_ROLL = "1CS21CS001";

    // ---- Roll and subjects ------------------------------------------------

    public static final String OTHER_ROLL = "1CS21CS002";
    public static final String SUBJECT_A = "CS501";
    public static final String SUBJECT_B = "CS502";
    public static final String SUBJECT_C = "CS503";

    /** A subject code no seeded record uses, for the empty-state journey. */
    public static final String UNUSED_SUBJECT = "ZZ999";

    // ---- Period allocation ------------------------------------------------

    /*
     * Attendance is unique on (student, subject, date, period) under BR-01,
     * so two journeys writing the same tuple make one of them fail for a
     * reason that has nothing to do with what it is testing. J3 took period
     * 7 and broke J1, whose own comment claimed 7 was free.
     *
     * One period per journey, named so the next addition can see what is
     * already taken:
     */
    public static final int PERIOD_J1_RECORD = 7;
    public static final int PERIOD_J3_SESSION = 6;
    public static final int PERIOD_J3_STRIKE = 4;
    public static final int PERIOD_J4_SELF_SERVICE = 5;

    /*
     * The suite assumes a freshly seeded instance, which the pipeline
     * provides by deploying a new container each run. It is not re-runnable
     * against an instance it has already written to: a journey that creates
     * a record is refused the second time by the same uniqueness rule it
     * relies on. Running it twice without redeploying will fail, and that
     * failure is the fixture, not the application.
     */
}
