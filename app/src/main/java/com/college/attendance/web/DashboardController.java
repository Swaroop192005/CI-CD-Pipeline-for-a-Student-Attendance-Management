package com.college.attendance.web;

import com.college.attendance.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Summary dashboard (FR-23). The service decides what the signed-in user
 * may see, so this controller does not need to know the viewer's role.
 */
@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("summary", dashboardService.summary());
        return "dashboard";
    }
}
