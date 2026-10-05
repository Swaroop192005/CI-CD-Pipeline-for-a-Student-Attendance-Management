package com.college.attendance.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the custom sign-in page.
 *
 * <p>A page of our own rather than Spring Security's default, because the
 * Selenium suite needs stable {@code data-testid} hooks and the error and
 * signed-out states need to be distinguishable from each other.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
