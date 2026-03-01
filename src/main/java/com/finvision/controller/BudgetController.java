package com.finvision.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.finvision.model.Budget;
import com.finvision.repository.BudgetRepository;

@Controller
public class BudgetController {

    @Autowired
    private BudgetRepository budgetRepository;

    @PostMapping("/budget")
    public String saveBudget(
            @RequestParam(defaultValue = "0") double monthlyIncome,
            @RequestParam(defaultValue = "0") double otherIncome,
            @RequestParam(defaultValue = "0") double rent,
            @RequestParam(required = false) String rentDueDate,
            @RequestParam(defaultValue = "0") double utilities,
            @RequestParam(required = false) String utilitiesDueDate,
            @RequestParam(defaultValue = "0") double insurance,
            @RequestParam(required = false) String insuranceDueDate,
            @RequestParam(defaultValue = "0") double groceries,
            @RequestParam(required = false) String groceriesDueDate,
            @RequestParam(defaultValue = "0") double subscriptions,
            @RequestParam(required = false) String subscriptionsDueDate,
            @RequestParam(required = false) List<String> variableTitle,
            // Accept as String to avoid "" -> Double conversion failures
            @RequestParam(required = false) List<String> variableAmount,
            @RequestParam(required = false) List<String> variableDueDate,
            Principal principal,
            Model model) {

        if (principal == null) {
            // Adjust to your login route if different
            return "redirect:/login";
        }

        String username = principal.getName();

        String currentMonth = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        model.addAttribute("currentMonth", currentMonth);

        Budget budget = budgetRepository.findByUsernameAndMonth(username, currentMonth)
                .orElseGet(Budget::new);

        budget.setUsername(username);
        budget.setMonth(currentMonth);

        budget.setMonthlyIncome(monthlyIncome);
        budget.setOtherIncome(otherIncome);
        budget.setRent(rent);
        budget.setUtilities(utilities);
        budget.setInsurance(insurance);
        budget.setGroceries(groceries);
        budget.setSubscriptions(subscriptions);
        budget.setRentDueDate(normalizeDate(rentDueDate));
        budget.setUtilitiesDueDate(normalizeDate(utilitiesDueDate));
        budget.setInsuranceDueDate(normalizeDate(insuranceDueDate));
        budget.setGroceriesDueDate(normalizeDate(groceriesDueDate));
        budget.setSubscriptionsDueDate(normalizeDate(subscriptionsDueDate));

        List<String> titles = (variableTitle != null) ? variableTitle : Collections.emptyList();
        List<String> amountsRaw = (variableAmount != null) ? variableAmount : Collections.emptyList();
        List<String> dueDatesRaw = (variableDueDate != null) ? variableDueDate : Collections.emptyList();

        // Normalize + parse safely
        int n = Math.max(Math.max(titles.size(), amountsRaw.size()), dueDatesRaw.size());
        List<String> cleanTitles = new ArrayList<>(n);
        List<Double> cleanAmounts = new ArrayList<>(n);
        List<String> cleanDueDates = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            String t = (i < titles.size() && titles.get(i) != null) ? titles.get(i).trim() : "";
            String a = (i < amountsRaw.size() && amountsRaw.get(i) != null) ? amountsRaw.get(i).trim() : "";
            String d = (i < dueDatesRaw.size() && dueDatesRaw.get(i) != null) ? dueDatesRaw.get(i).trim() : "";

            // Skip rows where both are empty
            if (t.isEmpty() && a.isEmpty() && d.isEmpty()) continue;

            cleanTitles.add(t);

            double parsed = 0.0;
            if (!a.isEmpty()) {
                try {
                    parsed = Double.parseDouble(a);
                } catch (NumberFormatException ignored) {
                    // Keep 0.0 if user typed something invalid
                }
            }
            cleanAmounts.add(parsed);
            cleanDueDates.add(normalizeDate(d));
        }

        budget.setVariableTitle(cleanTitles);
        budget.setVariableAmount(cleanAmounts);
        budget.setVariableDueDate(cleanDueDates);

        budgetRepository.save(budget);

        model.addAttribute("budget", budget);
        model.addAttribute("varTitles", budget.getVariableTitle());
        model.addAttribute("varAmounts", budget.getVariableAmount());
        model.addAttribute("varDueDates", budget.getVariableDueDate());
        model.addAttribute("success", "Budget saved successfully!");
        return "BudgetSet";
    }

    private String normalizeDate(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }
}
