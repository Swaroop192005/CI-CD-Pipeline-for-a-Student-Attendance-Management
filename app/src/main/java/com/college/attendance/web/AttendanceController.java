package com.college.attendance.web;

import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.dto.AttendanceForm;
import com.college.attendance.service.AttendanceException;
import com.college.attendance.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Attendance list, detail and creation (US-01, US-02).
 *
 * <p>Search, correction and the review workflow are added in Stage 6; this
 * controller is the first vertical slice through the whole stack.
 */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private static final int PAGE_SIZE = 10;

    private final AttendanceService attendanceService;
    private final CurrentUser currentUser;

    public AttendanceController(AttendanceService attendanceService, CurrentUser currentUser) {
        this.attendanceService = attendanceService;
        this.currentUser = currentUser;
    }

    @ModelAttribute("attendanceStatuses")
    public AttendanceStatus[] attendanceStatuses() {
        return AttendanceStatus.values();
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<AttendanceRecord> records =
                attendanceService.list(PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        model.addAttribute("records", records);
        model.addAttribute("currentPage", records.getNumber());
        return "attendance/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new AttendanceForm());
        model.addAttribute("students", attendanceService.activeStudents());
        return "attendance/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") AttendanceForm form,
                         BindingResult binding, Model model, RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("students", attendanceService.activeStudents());
            return "attendance/form";
        }
        try {
            AttendanceRecord saved = attendanceService.create(form);
            flash.addFlashAttribute("successMessage",
                    "Attendance recorded for " + saved.getStudent().getDisplayName()
                            + " in " + saved.getSubjectCode() + ". It is a draft until you submit it.");
            return "redirect:/attendance/" + saved.getId();
        } catch (AttendanceException | IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("students", attendanceService.activeStudents());
            return "attendance/form";
        }
    }

    /**
     * Record detail.
     *
     * <p>Not yet scoped per role: any authenticated user can open any
     * record. Record-level scoping for students (US-09, FR-25) lands with
     * the search predicate in Stage 6, so that the list and the detail
     * view are restricted by the same rule rather than by two that can
     * drift apart. The current behaviour is a known gap, not a decision.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        AttendanceRecord record = attendanceService.require(id);
        model.addAttribute("record", record);
        model.addAttribute("mayEdit", attendanceService.mayEdit(record));
        model.addAttribute("isAuthor", record.wasMarkedBy(currentUser.username()));
        return "attendance/detail";
    }
}
