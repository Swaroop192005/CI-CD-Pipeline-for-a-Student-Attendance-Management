package com.college.attendance.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entry point after sign-in.
 *
 * <p>Points at the attendance list while the dashboard is still to come
 * (US-08, Stage 6); the skeleton status page it replaced had served its
 * purpose of proving the stack end to end.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/attendance";
    }

    /** Placeholder until the summary dashboard lands in Stage 6. */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/attendance";
    }
}
