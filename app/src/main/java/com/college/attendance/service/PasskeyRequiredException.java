package com.college.attendance.service;

/**
 * The action needs a passkey the acting user has not registered, or the
 * passkey they presented did not verify.
 *
 * <p>Separate from {@link NotPermittedException} because the remedy is
 * different: the user is permitted, they simply have to enrol a passkey
 * first, and the page can say so.
 */
public class PasskeyRequiredException extends AttendanceException {

    public PasskeyRequiredException(String message) {
        super(message);
    }
}
