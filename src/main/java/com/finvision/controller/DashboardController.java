package com.finvision.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard() {
        return "Dashboard";
    }

    @GetMapping("/tracker")
    public String tracker() {
        return "Trackerpage";
    }

    @GetMapping("/budget")
    public String budget() {
        return "BudgetSet";
    }

    @GetMapping("/insights")
    public String insights() {
        return "Insightspage";
    }

}
