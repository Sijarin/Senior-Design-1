package com.finvision.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
    public String budget(Model model) {
        String currentMonth = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        model.addAttribute("currentMonth", currentMonth);
        return "BudgetSet";
    }

    @GetMapping("/insights")
    public String insights() {
        return "Insightspage";
    }

}
