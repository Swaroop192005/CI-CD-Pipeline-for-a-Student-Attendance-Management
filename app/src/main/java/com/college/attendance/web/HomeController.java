package com.college.attendance.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entry point after sign-in: every role lands on the dashboard, which
 * scopes itself to what that role may see.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }
}
