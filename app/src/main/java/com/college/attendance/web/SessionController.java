package com.college.attendance.web;

import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.AttendanceSession;
import com.college.attendance.service.AttendanceException;
import com.college.attendance.service.AttendanceSessionService;
import com.college.attendance.repository.AttendanceRecordRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    public SessionController(AttendanceSessionService sessions,
                             AttendanceRecordRepository records,
                             CurrentUser currentUser) {
        this.sessions = sessions;
        this.records = records;
        this.currentUser = currentUser;
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
        AttendanceSession session = sessions.require(id);
        model.addAttribute("session", session);
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
        return "sessions/mark";
    }

    @PostMapping("/{id}/mark")
    public String mark(@PathVariable Long id, RedirectAttributes flash) {
        try {
            sessions.markPresent(id);
            flash.addFlashAttribute("successMessage", "You are marked present for this session.");
        } catch (AttendanceException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sessions/mark";
    }
}
