package com.college.attendance.web;

import com.college.attendance.service.DuplicateRecordException;
import com.college.attendance.service.NotPermittedException;
import com.college.attendance.service.RecordLockedException;
import com.college.attendance.service.RecordNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Turns the domain's refusals into pages with the right status code.
 *
 * <p>Browser requests get a readable page; anything under {@code /api/}
 * is left to the REST layer's own handler so that API clients still get
 * JSON.
 */
@ControllerAdvice(basePackages = "com.college.attendance.web")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecordNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(RecordNotFoundException e, Model model) {
        model.addAttribute("statusCode", 404);
        model.addAttribute("statusText", "Record not found");
        model.addAttribute("detail", e.getMessage());
        return "error/problem";
    }

    @ExceptionHandler({NotPermittedException.class, AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden(Exception e, HttpServletRequest request, Model model) {
        log.warn("Refused {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        model.addAttribute("statusCode", 403);
        model.addAttribute("statusText", "Not permitted");
        model.addAttribute("detail", e.getMessage());
        return "error/problem";
    }

    @ExceptionHandler({DuplicateRecordException.class, RecordLockedException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(RuntimeException e, Model model) {
        model.addAttribute("statusCode", 409);
        model.addAttribute("statusText", "Cannot complete this change");
        model.addAttribute("detail", e.getMessage());
        return "error/problem";
    }
}
