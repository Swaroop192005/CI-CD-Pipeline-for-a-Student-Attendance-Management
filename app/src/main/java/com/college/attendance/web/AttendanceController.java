package com.college.attendance.web;

import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.dto.AttendanceForm;
import com.college.attendance.dto.AttendanceSearch;
import com.college.attendance.service.AttendanceException;
import com.college.attendance.service.AttendanceService;
import com.college.attendance.service.NotPermittedException;
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
 * Attendance list, search, detail, creation, correction and the workflow
 * actions (US-01 to US-07).
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

    // ---- Read -------------------------------------------------------------

    /**
     * Paginated list with the five optional filters (FR-13, FR-14).
     *
     * <p>The search object is put back in the model so the pagination
     * links can echo the active filters, which is what keeps them alive
     * across pages.
     */
    @GetMapping
    public String list(@ModelAttribute("search") AttendanceSearch search,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<AttendanceRecord> records =
                attendanceService.search(search, PageRequest.of(Math.max(page, 0), PAGE_SIZE));

        model.addAttribute("records", records);
        model.addAttribute("subjectCodes", attendanceService.knownSubjectCodes());
        model.addAttribute("filtersActive", search.isActive());
        return "attendance/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        AttendanceRecord record = attendanceService.require(id);
        model.addAttribute("record", record);
        model.addAttribute("mayEdit", attendanceService.mayEdit(record));
        model.addAttribute("mayEdit", attendanceService.mayEdit(record));
        model.addAttribute("isAuthor", record.wasMarkedBy(currentUser.username()));
        return "attendance/detail";
    }

    // ---- Create -----------------------------------------------------------

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

    // ---- Update -----------------------------------------------------------

    /**
     * The correction form.
     *
     * <p>Refuses outright if the record has left an editable state, rather
     * than rendering a form whose submission the service will reject. A
     * form that cannot be saved is worse than no form: the faculty member
     * types the correction before being told it was never possible.
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        AttendanceRecord record = attendanceService.require(id);
        if (!attendanceService.mayEdit(record)) {
            throw new NotPermittedException("Record " + id + " was entered by "
                    + record.getMarkedBy()
                    + " and only they or an administrator may correct it.");
        }
        model.addAttribute("form", AttendanceForm.from(record));
        model.addAttribute("students", attendanceService.activeStudents());
        return "attendance/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") AttendanceForm form,
                         BindingResult binding, Model model, RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("students", attendanceService.activeStudents());
            return "attendance/form";
        }
        try {
            attendanceService.update(id, form);
            flash.addFlashAttribute("successMessage", "Record corrected.");
            return "redirect:/attendance/" + id;
        } catch (AttendanceException | IllegalArgumentException e) {
            form.setId(id);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("students", attendanceService.activeStudents());
            return "attendance/form";
        }
    }

}
