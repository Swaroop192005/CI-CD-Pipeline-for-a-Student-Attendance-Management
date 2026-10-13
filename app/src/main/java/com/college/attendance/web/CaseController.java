package com.college.attendance.web;

import com.college.attendance.config.CurrentUser;
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
        model.addAttribute("cases", cases.all());
        model.addAttribute("studentView", false);
        // Students eligible for a case, minus anyone who already has one open.
        List<String> alreadyOpen = cases.open().stream()
                .map(c -> c.getStudent().getRollNumber()).toList();
        model.addAttribute("candidates", summary.inShortage().stream()
                .filter(s -> !alreadyOpen.contains(s.rollNumber()))
                .toList());
        model.addAttribute("shortageThreshold", summary.shortageThreshold());
        return "cases/index";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("shortageCase", cases.require(id));
        model.addAttribute("studentView", currentUser.isStudent());
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

    @PostMapping("/{id}/accept")
    public String accept(@PathVariable Long id,
                         @RequestParam(required = false) String note,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         RedirectAttributes flash) {
        try {
            var decided = cases.accept(id, note, from, to);
            flash.addFlashAttribute("successMessage",
                    "Explanation accepted. " + decided.getExcusedCount()
                    + " session(s) marked excused, which removes them from the percentage.");
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

    // ---- student ----------------------------------------------------------

    @PostMapping("/{id}/respond")
    public String respond(@PathVariable Long id,
                          @RequestParam String response,
                          RedirectAttributes flash) {
        try {
            cases.respond(id, response);
            flash.addFlashAttribute("successMessage",
                    "Your explanation has been sent to the Head of Department.");
        } catch (AttendanceException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }
}
