package com.college.attendance.web;

import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.CaseTier;
import com.college.attendance.domain.Role;
import com.college.attendance.dto.DashboardSummary;
import com.college.attendance.service.AttendanceException;
import com.college.attendance.service.DashboardService;
import com.college.attendance.service.ShortageCaseService;
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
 * Shortage cases: the head of department's queue, and the student's side of
 * the same conversation.
 *
 * <p>Both roles use this controller because it is one exchange, not two
 * features. Splitting it would make it easy for the two halves to drift
 * into disagreeing about what state a case is in.
 */
@Controller
@RequestMapping("/cases")
public class CaseController {

    private final ShortageCaseService cases;
    private final DashboardService dashboard;
    private final CurrentUser currentUser;

    public CaseController(ShortageCaseService cases, DashboardService dashboard,
                          CurrentUser currentUser) {
        this.cases = cases;
        this.dashboard = dashboard;
        this.currentUser = currentUser;
    }

    /** The queue for staff, or their own cases for a student. */
    @GetMapping
    public String index(Model model) {
        String roll = currentUser.restrictedToRollNumber().orElse(null);

        if (roll != null) {
            model.addAttribute("cases", cases.forStudent(roll));
            model.addAttribute("studentView", true);
            return "cases/index";
        }

        DashboardSummary summary = dashboard.summary();
        // Only this user's tier. Showing both would invite a decision on a
        // case belonging to the other role, which the service refuses --
        // better not to offer it.
        model.addAttribute("cases", cases.mine());
        model.addAttribute("candidates", cases.candidates());
        model.addAttribute("studentView", false);
        model.addAttribute("facultyView", currentUser.hasRole(Role.FACULTY)
                && !currentUser.isAdmin());
        model.addAttribute("shortageThreshold", summary.shortageThreshold());
        model.addAttribute("eligibilityThreshold", summary.eligibilityThreshold());
        return "cases/index";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        var shortage = cases.require(id);
        boolean faculty = currentUser.hasRole(Role.FACULTY) && !currentUser.isAdmin();
        model.addAttribute("shortageCase", shortage);
        model.addAttribute("studentView", currentUser.isStudent());
        model.addAttribute("facultyView", faculty);
        // Whether this viewer is the one with a move to make. The service
        // re-checks it; this only decides whether to draw the buttons.
        model.addAttribute("mayDecide", !currentUser.isStudent()
                && shortage.getStatus().awaitsDecision()
                && shortage.getTier() == (faculty ? CaseTier.FACULTY : CaseTier.HOD));
        model.addAttribute("today", LocalDate.now());
        return "cases/detail";
    }

    // ---- head of department -----------------------------------------------

    @PostMapping("/raise")
    public String raise(@RequestParam String rollNumber,
                        @RequestParam String question,
                        RedirectAttributes flash) {
        try {
            var raised = cases.raise(rollNumber, question);
            flash.addFlashAttribute("successMessage",
                    "Case raised. " + rollNumber + " will see it when they next sign in.");
            return "redirect:/cases/" + raised.getId();
        } catch (AttendanceException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/cases";
        }
    }

    /**
     * Accept the explanation.
     *
     * <p>{@code from} and {@code to} are optional because a faculty case
     * excuses nothing: requiring dates there would ask faculty for a window
     * that is never applied. The service refuses a head-of-department case
     * without them.
     */
    @PostMapping("/{id}/accept")
    public String accept(@PathVariable Long id,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         RedirectAttributes flash) {
        try {
            var decided = cases.accept(id, note, from, to);
            int excused = decided.getExcusedCount() == null ? 0 : decided.getExcusedCount();
            flash.addFlashAttribute("successMessage", excused > 0
                    ? "Explanation accepted. " + excused + " session(s) marked excused, "
                      + "which removes them from the percentage."
                    : "Explanation accepted and the case is closed. No sessions were excused, "
                      + "so the attendance figure is unchanged.");
        } catch (AttendanceException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(required = false) String note,
                         RedirectAttributes flash) {
        try {
            cases.reject(id, note);
            flash.addFlashAttribute("successMessage", "Explanation rejected; the shortage stands.");
        } catch (AttendanceException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }

    @PostMapping("/{id}/escalate")
    public String escalate(@PathVariable Long id,
                           @RequestParam(required = false) String note,
                           RedirectAttributes flash) {
        try {
            cases.escalate(id, note);
            flash.addFlashAttribute("successMessage",
                    "Passed to the Head of Department with the student's reply attached.");
        } catch (AttendanceException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }

    // ---- student ----------------------------------------------------------

    @PostMapping("/{id}/respond")
    public String respond(@PathVariable Long id,
                          @RequestParam String response,
                          RedirectAttributes flash) {
        try {
            var answered = cases.respond(id, response);
            flash.addFlashAttribute("successMessage", "Your explanation has been sent to "
                    + (answered.getTier() == CaseTier.FACULTY
                       ? "your faculty." : "the Head of Department."));
        } catch (AttendanceException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }
}
