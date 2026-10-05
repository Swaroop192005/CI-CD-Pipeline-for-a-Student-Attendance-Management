package com.college.attendance.web;

import com.college.attendance.service.AttendanceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The administrator's view of the student roll (FR-08 of the engineering
 * scope; US-08 of the backlog).
 */
@Controller
public class StudentController {

    private final AttendanceService attendanceService;

    public StudentController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/students")
    public String roll(Model model) {
        model.addAttribute("students", attendanceService.activeStudents());
        return "students";
    }
}
