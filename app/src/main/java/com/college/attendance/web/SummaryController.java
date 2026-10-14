package com.college.attendance.web;

import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.Student;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.service.AttendanceSummaryService;
import com.college.attendance.service.NotPermittedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

/**
 * Attendance read week by week and month by month.
 *
 * <p>A student sees only their own figures. The roll number is taken from
 * their account, not from the query string, so changing {@code ?rollNumber=}
 * cannot widen what they see -- staff get the selector, students get
 * themselves regardless of what they ask for.
 */
@Controller
@RequestMapping("/summary")
public class SummaryController {

    private final AttendanceSummaryService summaries;
    private final StudentRepository students;
    private final CurrentUser currentUser;

    public SummaryController(AttendanceSummaryService summaries,
                              StudentRepository students,
                              CurrentUser currentUser) {
        this.summaries = summaries;
        this.students = students;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String summary(@RequestParam(required = false) String rollNumber, Model model) {
        Optional<String> ownRoll = currentUser.restrictedToRollNumber();

        // A student is pinned to their own roll number; the request parameter
        // is ignored rather than validated, because there is no case in which
        // honouring it would be correct.
        String roll = ownRoll.orElse(rollNumber);
        boolean staff = ownRoll.isEmpty();

        if (staff) {
            model.addAttribute("roll", students.findByActiveTrueOrderByRollNumberAsc());
        }

        if (roll == null || roll.isBlank()) {
            model.addAttribute("staff", true);
            model.addAttribute("selected", null);
            return "summary/index";
        }

        Student student = students.findByRollNumber(roll)
                .orElseThrow(() -> new NotPermittedException("No student record for " + roll + "."));

        List<?> weekly = summaries.weekly(roll);
        model.addAttribute("staff", staff);
        model.addAttribute("selected", student);
        model.addAttribute("weekly", weekly);
        model.addAttribute("monthly", summaries.monthly(roll));
        model.addAttribute("nothingRecorded", weekly.isEmpty());
        return "summary/index";
    }
}
