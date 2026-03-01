package com.finvision.controller;


import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    public String dashboard(Principal principal, Model model) {
        String username = (principal != null) ? principal.getName() : "User";
        model.addAttribute("username", username);
        model.addAttribute("currentDate",
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));

        Budget budget = budgetRepository.findAll()
                .stream()
                .reduce((first, second) -> second)
                .orElse(null);

        if (budget != null) {
            double income   = budget.getMonthlyIncome() + budget.getOtherIncome();
            double expenses = budget.getRent() + budget.getUtilities() +
                              budget.getInsurance() + budget.getGroceries() +
                              budget.getSubscriptions();

            java.util.List<Double> varAmounts = budget.getVariableAmount();
            if (varAmounts != null) {
                for (Double amt : varAmounts) { if (amt != null && amt > 0) expenses += amt; }
            }

            double savings     = income - expenses;
            double savingsRate = (income == 0) ? 0 : Math.max((savings / income) * 100, 0);
            double percent     = (income == 0) ? 0 : Math.min((expenses / income) * 100, 100);
            String status      = savings < 0 ? "overspending" : (savings < income * 0.2 ? "low" : "healthy");

            // Pie chart data
            java.util.List<String> pieLabels = new java.util.ArrayList<>();
            java.util.List<Double> pieData   = new java.util.ArrayList<>();
            if (budget.getRent()          > 0) { pieLabels.add("Rent");          pieData.add(budget.getRent()); }
            if (budget.getUtilities()     > 0) { pieLabels.add("Utilities");     pieData.add(budget.getUtilities()); }
            if (budget.getInsurance()     > 0) { pieLabels.add("Insurance");     pieData.add(budget.getInsurance()); }
            if (budget.getGroceries()     > 0) { pieLabels.add("Groceries");     pieData.add(budget.getGroceries()); }
            if (budget.getSubscriptions() > 0) { pieLabels.add("Subscriptions"); pieData.add(budget.getSubscriptions()); }
            java.util.List<String> varTitles = budget.getVariableTitle();
            if (varTitles != null && varAmounts != null) {
                for (int i = 0; i < Math.min(varTitles.size(), varAmounts.size()); i++) {
                    if (varAmounts.get(i) != null && varAmounts.get(i) > 0) {
                        pieLabels.add(varTitles.get(i));
                        pieData.add(varAmounts.get(i));
                    }
                }
            }

            model.addAttribute("hasBudget",   true);
            model.addAttribute("income",      String.format("%.2f", income));
            model.addAttribute("expenses",    String.format("%.2f", expenses));
            model.addAttribute("savings",     String.format("%.2f", savings));
            model.addAttribute("savingsRate", String.format("%.0f", savingsRate));
            model.addAttribute("percent",     String.format("%.0f", percent));
            model.addAttribute("status",      status);
            model.addAttribute("pieLabels",   pieLabels);
            model.addAttribute("pieData",     pieData);
        } else {
            model.addAttribute("hasBudget", false);
            model.addAttribute("status", "none");
        }

        return "Dashboard";
    }

    @GetMapping("/spending-visualization")
    public String spendingVisualization(Model model) {
        Budget budget = budgetRepository.findAll()
                .stream()
                .reduce((first, second) -> second)
                .orElse(null);

        if (budget != null) {
            double income = budget.getMonthlyIncome() + budget.getOtherIncome();
            double rent = budget.getRent();
            double utilities = budget.getUtilities();
            double insurance = budget.getInsurance();
            double groceries = budget.getGroceries();
            double subscriptions = budget.getSubscriptions();
            double totalExpenses = rent + utilities + insurance + groceries + subscriptions;

            java.util.List<String> pieLabels = new java.util.ArrayList<>();
            java.util.List<Double> pieData = new java.util.ArrayList<>();
            if (rent > 0)          { pieLabels.add("Rent");          pieData.add(rent); }
            if (utilities > 0)     { pieLabels.add("Utilities");     pieData.add(utilities); }
            if (insurance > 0)     { pieLabels.add("Insurance");     pieData.add(insurance); }
            if (groceries > 0)     { pieLabels.add("Groceries");     pieData.add(groceries); }
            if (subscriptions > 0) { pieLabels.add("Subscriptions"); pieData.add(subscriptions); }

            java.util.List<String> varTitles = budget.getVariableTitle();
            java.util.List<Double> varAmounts = budget.getVariableAmount();
            if (varTitles != null && varAmounts != null) {
                for (int i = 0; i < Math.min(varTitles.size(), varAmounts.size()); i++) {
                    if (varAmounts.get(i) != null && varAmounts.get(i) > 0) {
                        pieLabels.add(varTitles.get(i));
                        pieData.add(varAmounts.get(i));
                        totalExpenses += varAmounts.get(i);
                    }
                }
            }

            model.addAttribute("income", income);
            model.addAttribute("totalExpenses", totalExpenses);
            model.addAttribute("pieLabels", pieLabels);
            model.addAttribute("pieData", pieData);
            model.addAttribute("hasBudget", true);
        } else {
            model.addAttribute("hasBudget", false);
        }

        return "SpendingVisualization";
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

@GetMapping("/predictive-cash-flow")
public String predictiveCashFlow(Model model) {
    Budget budget = budgetRepository.findAll()
            .stream()
            .reduce((first, second) -> second)
            .orElse(null);

    if (budget != null) {
        double income   = budget.getMonthlyIncome() + budget.getOtherIncome();
        double expenses = budget.getRent() + budget.getUtilities() +
                          budget.getInsurance() + budget.getGroceries() +
                          budget.getSubscriptions();

        java.util.List<Double> varAmounts = budget.getVariableAmount();
        if (varAmounts != null) {
            for (Double amt : varAmounts) { if (amt != null && amt > 0) expenses += amt; }
        }

        double monthlySavings = income - expenses;
        double savingsRate    = (income == 0) ? 0 : (monthlySavings / income) * 100;

        // Build 6-month forecast (labels + cumulative balances)
        java.util.List<String> monthLabels      = new java.util.ArrayList<>();
        java.util.List<String> projectedBalances = new java.util.ArrayList<>();
        java.util.List<String> monthlyIncomes    = new java.util.ArrayList<>();
        java.util.List<String> monthlyExpenses   = new java.util.ArrayList<>();

        LocalDate cursor = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
        double cumulative = 0;
        for (int i = 0; i < 6; i++) {
            cumulative += monthlySavings;
            monthLabels.add(cursor.plusMonths(i).format(fmt));
            projectedBalances.add(String.format("%.2f", cumulative));
            monthlyIncomes.add(String.format("%.2f", income));
            monthlyExpenses.add(String.format("%.2f", expenses));
        }

        String trajectory = monthlySavings > 0 ? "positive" : (monthlySavings < 0 ? "negative" : "neutral");

        model.addAttribute("hasBudget",          true);
        model.addAttribute("income",             String.format("%.2f", income));
        model.addAttribute("expenses",           String.format("%.2f", expenses));
        model.addAttribute("monthlySavings",     String.format("%.2f", monthlySavings));
        model.addAttribute("savingsRate",        String.format("%.0f", savingsRate));
        model.addAttribute("sixMonthTotal",      String.format("%.2f", cumulative));
        model.addAttribute("trajectory",         trajectory);
        model.addAttribute("monthLabels",        monthLabels);
        model.addAttribute("projectedBalances",  projectedBalances);
        model.addAttribute("monthlyIncomes",     monthlyIncomes);
        model.addAttribute("monthlyExpenses",    monthlyExpenses);
    } else {
        model.addAttribute("hasBudget", false);
        model.addAttribute("trajectory", "none");
    }

    model.addAttribute("currentMonth",
        LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));

    return "PredictiveCashFlow";
}

@GetMapping("/alerts")
public String alerts(Model model) {
    Budget budget = budgetRepository.findAll()
            .stream()
            .reduce((first, second) -> second)
            .orElse(null);

    if (budget != null) {
        double income   = budget.getMonthlyIncome() + budget.getOtherIncome();
        double expenses = budget.getRent() + budget.getUtilities() +
                          budget.getInsurance() + budget.getGroceries() +
                          budget.getSubscriptions();

        java.util.List<Double> varAmounts = budget.getVariableAmount();
        if (varAmounts != null) {
            for (Double amt : varAmounts) { if (amt != null && amt > 0) expenses += amt; }
        }

        double remaining = income - expenses;
        double percent   = (income == 0) ? 0 : Math.min((expenses / income) * 100, 100);
        String status    = remaining < 0 ? "overspending" : (remaining < income * 0.2 ? "low" : "healthy");

        model.addAttribute("hasBudget",  true);
        model.addAttribute("status",     status);
        model.addAttribute("income",     String.format("%.2f", income));
        model.addAttribute("expenses",   String.format("%.2f", expenses));
        model.addAttribute("remaining",  String.format("%.2f", remaining));
        model.addAttribute("percent",    String.format("%.0f", percent));
        model.addAttribute("rent",         String.format("%.2f", budget.getRent()));
        model.addAttribute("utilities",    String.format("%.2f", budget.getUtilities()));
        model.addAttribute("insurance",    String.format("%.2f", budget.getInsurance()));
        model.addAttribute("groceries",    String.format("%.2f", budget.getGroceries()));
        model.addAttribute("subscriptions",String.format("%.2f", budget.getSubscriptions()));
        model.addAttribute("varTitles",    budget.getVariableTitle());
        model.addAttribute("varAmounts",   budget.getVariableAmount());
    } else {
        model.addAttribute("hasBudget", false);
        model.addAttribute("status", "none");
    }

    model.addAttribute("currentMonth",
        LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));

    return "AlertsNotifications";
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
    String status;

    if (budget != null) {
        income = budget.getMonthlyIncome() + budget.getOtherIncome();
        expenses = budget.getRent() + budget.getUtilities() +
                   budget.getInsurance() + budget.getGroceries() +
                   budget.getSubscriptions();

        // Include variable expenses
        java.util.List<Double> varAmounts = budget.getVariableAmount();
        if (varAmounts != null) {
            for (Double amt : varAmounts) {
                if (amt != null && amt > 0) expenses += amt;
            }
        }

        remaining = income - expenses;
        percent = (income == 0) ? 0 : Math.min((expenses / income) * 100, 100);

        if (remaining < 0)            status = "overspending";
        else if (remaining < income * 0.2) status = "low";
        else                           status = "healthy";
    } else {
        income = 0; expenses = 0; remaining = 0; percent = 0;
        status = "none";
    }

    model.addAttribute("income",    String.format("%.2f", income));
    model.addAttribute("expenses",  String.format("%.2f", expenses));
    model.addAttribute("remaining", String.format("%.2f", remaining));
    model.addAttribute("percent",   String.format("%.0f", percent));
    model.addAttribute("status",    status);

    return "InsightsPage";
}
}