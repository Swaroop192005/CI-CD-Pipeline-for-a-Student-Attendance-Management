package com.college.attendance.config;

/**
 * A student account exists but carries no roll number, so there is no
 * way to work out which records it may see.
 *
 * <p>Thrown rather than silently widening the account's scope: a
 * misconfigured account must be a loud failure, never an unrestricted
 * one.
 */
public class MisconfiguredAccountException extends RuntimeException {

    public MisconfiguredAccountException(String username) {
        super("Account '" + username + "' has the student role but no linked roll number, "
                + "so its attendance view cannot be scoped. Ask an administrator to set "
                + "the roll number on this account.");
    }
}
