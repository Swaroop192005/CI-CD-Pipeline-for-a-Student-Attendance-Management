package com.college.attendance.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Renders the access-denied page that the security filter chain forwards
 * to, so that a refused request explains itself instead of showing a bare
 * container error.
 *
 * <p>Mapped for every HTTP method, not just GET. The filter chain forwards
 * the original request, so a refused POST arrives here as a POST: a
 * GET-only mapping answered it with 405 Method Not Allowed, which reads
 * as "wrong verb" when the truth is "wrong role". The status is set
 * explicitly for the same reason - a forwarded page that returns 200
 * would tell an API client the request succeeded.
 */
@Controller
public class ErrorPageController {

    @RequestMapping("/error/403")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accessDenied(Model model) {
        model.addAttribute("statusCode", 403);
        model.addAttribute("statusText", "Not permitted");
        model.addAttribute("detail",
                "Your role does not allow this action. If you believe this is wrong, "
                        + "ask an administrator to check your account's role.");
        return "error/problem";
    }
}
