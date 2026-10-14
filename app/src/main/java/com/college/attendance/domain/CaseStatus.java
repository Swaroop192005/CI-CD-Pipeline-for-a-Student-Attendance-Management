package com.college.attendance.domain;

/**
 * Where a shortage case has got to.
 *
 * <p>A case is a conversation, not a vote: whoever raised it asks for an
 * explanation, the student answers, and the person who asked then decides.
 *
 * <p>The permitted moves are deliberately few. Anything else is refused by
 * {@code ShortageCaseService}, so a case cannot be decided before the
 * student has had a chance to reply.
 *
 * <pre>
 *   AWAITING_RESPONSE --(student replies)------&gt; ANSWERED
 *   ANSWERED          --(faculty satisfied)---&gt; ACCEPTED
 *   ANSWERED          --(faculty escalates)---&gt; ESCALATED
 *   ANSWERED          --(head accepts)--------&gt; ACCEPTED
 *   ANSWERED          --(head rejects)--------&gt; REJECTED
 *   ESCALATED         --(head accepts)--------&gt; ACCEPTED
 *   ESCALATED         --(head rejects)--------&gt; REJECTED
 * </pre>
 *
 * <p>{@code ESCALATED} is open, not decided. The student has already
 * answered and is not asked again: the reply, and faculty's reason for not
 * being satisfied by it, are what the head of department reads. Treating it
 * as decided would close a case nobody has actually ruled on.
 */
public enum CaseStatus {

    /** Raised and sent to the student; waiting for them to explain. */
    AWAITING_RESPONSE,

    /** The student has replied; waiting for whoever raised the case. */
    ANSWERED,

    /**
     * Faculty were not satisfied by the reply and passed the case to the
     * head of department, who decides on the conversation as it stands.
     */
    ESCALATED,

    /**
     * The explanation was accepted. The sessions the head of department
     * chose to excuse have been marked {@code EXCUSED}, which removes them
     * from both sides of the percentage under BR-03.
     */
    ACCEPTED,

    /** The explanation was not accepted. The shortage stands. */
    REJECTED;

    public boolean isOpen() {
        return this == AWAITING_RESPONSE || this == ANSWERED || this == ESCALATED;
    }

    /** Whether the person who raised this case still has a move to make. */
    public boolean awaitsDecision() {
        return this == ANSWERED || this == ESCALATED;
    }

    public boolean isDecided() {
        return this == ACCEPTED || this == REJECTED;
    }
}
