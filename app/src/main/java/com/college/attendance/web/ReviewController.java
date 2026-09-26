package com.college.attendance.web;

import com.college.attendance.service.WorkflowService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The Head of Department's review queue (FR-22).
 */
@Controller
public class ReviewController {

    private final WorkflowService workflowService;

    public ReviewController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/review")
    public String queue(Model model) {
        model.addAttribute("pending", workflowService.reviewQueue());
        return "review/queue";
    }
}
