package com.college.attendance.domain;

/**
 * Where a shortage case has got to.
 *
 * <p>A case is raised against a student whose overall attendance has fallen
 * below the shortage threshold. It is a conversation, not a vote: the head
 * of department asks for an explanation, the student answers, and the head
 * of department then decides.
 *
 * <p>The permitted moves are deliberately few. Anything else is refused by
 * {@code ShortageCaseService}, so a case cannot be decided before the
 * student has had a chance to reply.
 *
 * <pre>
 *   AWAITING_RESPONSE --(student replies)--&gt; ANSWERED
 *   ANSWERED          --(head accepts)-----&gt; ACCEPTED
 *   ANSWERED          --(head rejects)-----&gt; REJECTED
 * </pre>
 */
public enum CaseStatus {

    /** Raised and sent to the student; waiting for them to explain. */
    AWAITING_RESPONSE,

    /** The student has replied; waiting for the head of department. */
    ANSWERED,

    /**
     * The explanation was accepted. The sessions the head of department
     * chose to excuse have been marked {@code EXCUSED}, which removes them
     * from both sides of the percentage under BR-03.
     */
    ACCEPTED,

    /** The explanation was not accepted. The shortage stands. */
    REJECTED;

    public boolean isOpen() {
        return this == AWAITING_RESPONSE || this == ANSWERED;
    }

    public boolean isDecided() {
        return this == ACCEPTED || this == REJECTED;
    }
}
