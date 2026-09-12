package com.college.attendance.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the skeleton landing page.
 *
 * <p>Once the dashboard feature lands (US-08), this becomes a redirect to
 * {@code /dashboard}; keeping a real rendered page here for the skeleton
 * means the initial commit is independently verifiable.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}
