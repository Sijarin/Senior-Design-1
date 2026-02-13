package com.finvision.controller;

import com.finvision.model.Budget;
import com.finvision.repository.BudgetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

@Controller
public class BudgetController {

    @Autowired
    private BudgetRepository budgetRepository;

    @PostMapping("/budget")
    public String saveBudget(
            @RequestParam double monthlyIncome,
            @RequestParam(defaultValue = "0") double otherIncome,
            @RequestParam(defaultValue = "0") double rent,
            @RequestParam(defaultValue = "0") double utilities,
            @RequestParam(defaultValue = "0") double insurance,
            @RequestParam(defaultValue = "0") double groceries,
            @RequestParam(defaultValue = "0") double subscriptions,
            @RequestParam(required = false) List<String> variableTitle,
            @RequestParam(required = false) List<Double> variableAmount,
            Principal principal,
            Model model) {

        Budget budget = new Budget();

        // Prevent null principal
        if (principal != null) {
            budget.setUsername(principal.getName());
        }

        String currentMonth = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"));
        budget.setMonth(currentMonth);
        model.addAttribute("currentMonth", currentMonth);

        budget.setMonthlyIncome(monthlyIncome);
        budget.setOtherIncome(otherIncome);
        budget.setRent(rent);
        budget.setUtilities(utilities);
        budget.setInsurance(insurance);
        budget.setGroceries(groceries);
        budget.setSubscriptions(subscriptions);

        // Prevent null list errors
        budget.setVariableTitle(
                variableTitle != null ? variableTitle : Collections.emptyList());

        budget.setVariableAmount(
                variableAmount != null ? variableAmount : Collections.emptyList());

        budgetRepository.save(budget);

        model.addAttribute("success", "Budget saved successfully!");
        return "BudgetSet";
    }
}
