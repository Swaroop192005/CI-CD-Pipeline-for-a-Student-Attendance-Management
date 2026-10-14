package com.college.attendance.web;

import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.AttendanceSession;
import com.college.attendance.service.AttendanceException;
import com.college.attendance.service.AttendanceSessionService;
import com.college.attendance.service.PasskeyAssertionService;
import com.college.attendance.service.PasskeyRequiredException;
import com.college.attendance.repository.AttendanceRecordRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * Taking attendance: faculty open and close a session, students mark
 * themselves while it is open.
 *
 * <p>Nothing here takes the student's identity from the request. A student
 * marking attendance is identified from the security context, so editing a
 * hidden form field cannot mark somebody else present.
 */
@Controller
@RequestMapping("/sessions")
public class SessionController {

    private final AttendanceSessionService sessions;
    private final AttendanceRecordRepository records;
    private final CurrentUser currentUser;
    private final PasskeyAssertionService passkeys;

    public SessionController(AttendanceSessionService sessions,
                             AttendanceRecordRepository records,
                             CurrentUser currentUser,
                             PasskeyAssertionService passkeys) {
        this.sessions = sessions;
        this.records = records;
        this.currentUser = currentUser;
        this.passkeys = passkeys;
    }

    /** Faculty view: open a session, or watch one that is running. */
    @GetMapping
    public String index(Model model) {
        model.addAttribute("openSessions", sessions.openSessions());
        model.addAttribute("recent", sessions.recent());
        model.addAttribute("subjects", records.findDistinctSubjectCodes());
        model.addAttribute("today", LocalDate.now());
        return "sessions/index";
    }

    @PostMapping("/open")
    public String open(@RequestParam String subjectCode,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sessionDate,
                       @RequestParam int periodNumber,
                       RedirectAttributes flash) {
        try {
            AttendanceSession opened = sessions.open(subjectCode, sessionDate, periodNumber);
            return "redirect:/sessions/" + opened.getId();
        } catch (AttendanceException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/sessions";
        }
    }

    /** The live roster for one session. */
    @GetMapping("/{id}")
    public String roster(@PathVariable Long id, Model model) {
        // Deliberately NOT called "session": Thymeleaf reserves that name for
        // the HTTP session, so ${session.open} resolved against servlet
        // session attributes instead of this object. It did not fail loudly
        // -- it rendered an empty heading and silently hid the close and
        // strike controls, because a missing property is simply false.
        model.addAttribute("teachingSession", sessions.require(id));
        model.addAttribute("marked", sessions.markedSoFar(id));
        return "sessions/roster";
    }

    @PostMapping("/{id}/strike")
    public String strike(@PathVariable Long id,
                         @RequestParam String rollNumber,
                         @RequestParam(required = false) String reason,
                         RedirectAttributes flash) {
        try {
            sessions.strike(id, rollNumber, reason);
            flash.addFlashAttribute("successMessage",
                    rollNumber + " recorded absent. The override is logged against your name.");
        } catch (AttendanceException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sessions/" + id;
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, RedirectAttributes flash) {
        try {
            AttendanceSession closed = sessions.close(id);
            flash.addFlashAttribute("successMessage",
                    "Session closed. " + closed.getMarkedCount() + " marked present, "
                    + closed.getAbsentCount() + " recorded absent.");
        } catch (AttendanceException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sessions";
    }

    // ---- student ----------------------------------------------------------

    /** What a student sees: the sessions they can still mark. */
    @GetMapping("/mark")
    public String markPage(Model model) {
        String roll = currentUser.restrictedToRollNumber().orElse(null);
        List<AttendanceSession> awaiting = roll == null
                ? List.of()
                : sessions.openSessionsAwaiting(roll);
        model.addAttribute("awaiting", awaiting);
        model.addAttribute("rollNumber", roll);
        model.addAttribute("passkeyRequired", passkeys.isRequiredToMark());
        return "sessions/mark";
    }

    /**
     * Issue a passkey challenge for the student about to mark this session.
     *
     * <p>Returns the options as JSON rather than rendering a page, because
     * the browser hands them straight to {@code navigator.credentials.get()}.
     */
    @PostMapping(value = "/{id}/mark/challenge", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<String> markChallenge(@PathVariable Long id, HttpSession httpSession) {
        // Checked before a challenge is issued so that a student whose
        // session has already closed gets the real reason, rather than a
        // Touch ID prompt followed by a refusal.
        sessions.requireOpen(id);
        try {
            return ResponseEntity.ok(passkeys.challengeJson(httpSession));
        } catch (AttendanceException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":" + quote(e.getMessage()) + "}");
        }
    }

    /**
     * Mark the signed-in student present, but only on a verified assertion.
     *
     * <p>{@code assertion} is required, so a request that omits it is
     * rejected by the binding before any attendance is written. That matters
     * more than it looks: an optional parameter would mean a hand-rolled POST
     * could mark attendance with no passkey at all, and the biometric would
     * be decoration.
     */
    @PostMapping("/{id}/mark")
    public String mark(@PathVariable Long id,
                       @RequestParam(required = false) String assertion,
                       HttpSession httpSession,
                       RedirectAttributes flash) {
        try {
            boolean verified = false;
            if (passkeys.isRequiredToMark()) {
                // Required: a missing assertion is refused here rather than
                // bound as optional and quietly ignored, which would leave the
                // biometric as decoration.
                if (assertion == null || assertion.isBlank()) {
                    throw new PasskeyRequiredException(
                            "Marking attendance needs your passkey. Reload the page and try again.");
                }
                passkeys.verify(assertion, httpSession);
                verified = true;
            } else if (assertion != null && !assertion.isBlank()) {
                // Not required, but one was offered: still verify it, so an
                // assertion is never accepted without being checked.
                passkeys.verify(assertion, httpSession);
                verified = true;
            }
            sessions.markPresent(id);
            flash.addFlashAttribute("successMessage", verified
                    ? "Passkey verified. You are marked present for this session."
                    : "You are marked present for this session.");
        } catch (AttendanceException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sessions/mark";
    }

    /** Minimal JSON string quoting, so an error message cannot break the body. */
    private static String quote(String text) {
        return "\"" + (text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }
}
