package com.finvision.controller;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.finvision.model.Budget;
import com.finvision.repository.BudgetRepository;
@Controller
public class DashboardController {

    @Autowired
    private BudgetRepository budgetRepository;

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
public String insights(Model model) {
  Budget budget = budgetRepository.findAll()
        .stream()
        .reduce((first, second) -> second)
        .orElse(null);

double income;
double expenses;
double remaining;
double percent;
List<String> suggestions;

if (budget != null) {
    income = budget.getMonthlyIncome() + budget.getOtherIncome();

    expenses =
            budget.getRent() +
            budget.getUtilities() +
            budget.getInsurance() +
            budget.getGroceries() +
            budget.getSubscriptions();

    remaining = income - expenses;
    percent = (income == 0) ? 0 : (expenses / income) * 100;

    suggestions = null;

    if (remaining < 0) {
        suggestions = List.of(
                "You are overspending by $" + String.format("%.2f", Math.abs(remaining)) + ".",
                "Your expenses are about " + String.format("%.0f", percent) + "% of your income.",
                "Reduce discretionary spending like dining, subscriptions, or shopping."
        );
    }
    else if (remaining < income * 0.2) {
        suggestions = List.of(
                "Your remaining balance is low.",
                "Try to limit non-essential expenses this month.",
                "Track daily spending to stay within budget."
        );
    }
    else {
        suggestions = List.of(
                "Great job! You are within your budget.",
                "Your expenses are about " + String.format("%.0f", percent) + "% of your income.",
                "Consider saving or investing part of your remaining amount."
        );
    }
} else {
    // No budget found: provide defaults
    income = 0;
    expenses = 0;
    remaining = 0;
    percent = 0;
    suggestions = List.of("No budget data available. Please set up your budget.");
}

model.addAttribute("income", String.format("%.2f", income));
model.addAttribute("expenses", String.format("%.2f", expenses));
model.addAttribute("remaining", String.format("%.2f", remaining));
model.addAttribute("suggestions", suggestions);

return "InsightsPage";
}
}