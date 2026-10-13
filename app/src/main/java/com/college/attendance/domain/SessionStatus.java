package com.college.attendance.domain;

/**
 * Whether a teaching session is still accepting marks.
 *
 * <p>Only two states, and the move between them is one-way. Re-opening a
 * closed session would let a student mark themselves present for a class
 * that finished hours ago, which is the abuse this window exists to
 * prevent; a mistake is corrected by faculty editing the record, where the
 * audit trail records who changed it.
 */
public enum SessionStatus {

    /** Students may mark themselves present. */
    OPEN,

    /** Closed by faculty; absences have been written for anyone who did not mark. */
    CLOSED
}
