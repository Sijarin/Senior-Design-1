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

/**
 * Handles the budget-save form submission on the Budget Set page.
 *
 * <p>Performs an upsert: if a budget already exists for the current user and
 * calendar month it is updated in-place; otherwise a new document is created.</p>
 */
@Controller
public class BudgetController {

    @Autowired
    private BudgetRepository budgetRepository;

    /**
     * Saves or updates the monthly budget for the authenticated user.
     *
     * @param monthlyIncome  primary monthly income amount
     * @param otherIncome    additional income amount (defaults to 0)
     * @param rent           budgeted rent expense (defaults to 0)
     * @param utilities      budgeted utilities expense (defaults to 0)
     * @param insurance      budgeted insurance expense (defaults to 0)
     * @param groceries      budgeted groceries expense (defaults to 0)
     * @param subscriptions  budgeted subscriptions expense (defaults to 0)
     * @param variableTitle  list of user-defined variable expense names
     * @param variableAmount list of amounts corresponding to each variable expense
     * @param principal      the currently authenticated user
     * @param model          Spring MVC model
     * @return the {@code BudgetSet} template with updated budget data pre-filled
     */
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

        String username = (principal != null) ? principal.getName() : null;

        String currentMonth = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"));
        model.addAttribute("currentMonth", currentMonth);

        // Upsert: update existing budget for this user+month, or create new
        Budget budget = null;
        if (username != null) {
            budget = budgetRepository.findByUsernameAndMonth(username, currentMonth).orElse(null);
        }
        if (budget == null) {
            budget = new Budget();
            budget.setUsername(username);
            budget.setMonth(currentMonth);
        }

        budget.setMonthlyIncome(monthlyIncome);
        budget.setOtherIncome(otherIncome);
        budget.setRent(rent);
        budget.setUtilities(utilities);
        budget.setInsurance(insurance);
        budget.setGroceries(groceries);
        budget.setSubscriptions(subscriptions);
        budget.setVariableTitle(variableTitle != null ? variableTitle : Collections.emptyList());
        budget.setVariableAmount(variableAmount != null ? variableAmount : Collections.emptyList());

        budgetRepository.save(budget);

        // Pass saved values back so form stays pre-filled
        model.addAttribute("budget", budget);
        model.addAttribute("varTitles", budget.getVariableTitle());
        model.addAttribute("varAmounts", budget.getVariableAmount());
        model.addAttribute("success", "Budget saved successfully!");
        return "BudgetSet";
    }
}
