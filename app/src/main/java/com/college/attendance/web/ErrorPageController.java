package com.college.attendance.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Renders the access-denied page that the security filter chain forwards
 * to, so that a refused request explains itself instead of showing a bare
 * container error.
 */
@Controller
public class ErrorPageController {

    @GetMapping("/error/403")
    public String accessDenied(Model model) {
        model.addAttribute("statusCode", 403);
        model.addAttribute("statusText", "Not permitted");
        model.addAttribute("detail",
                "Your role does not allow this action. If you believe this is wrong, "
                        + "ask an administrator to check your account's role.");
        return "error/problem";
    }
}
