package com.finvision.controller;


import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.finvision.model.Budget;
import com.finvision.model.ScannedReceipt;
import com.finvision.repository.BudgetRepository;
import com.finvision.repository.ScannedReceiptRepository;

@Controller
public class DashboardController {

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private ScannedReceiptRepository receiptRepository;

    // ─── Helper: sum this month's scanned receipts for a user ───────────────
    private double receiptTotal(String username) {
        String ym = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return receiptRepository.findByUsernameAndYearMonth(username, ym)
            .stream().mapToDouble(ScannedReceipt::getAmount).sum();
    }

    // ─── Helper: build pie-chart entries from receipts ──────────────────────
    private void addReceiptsToPie(String username,
            java.util.List<String> pieLabels, java.util.List<Double> pieData) {
        String ym = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        java.util.Map<String, Double> byCat = new java.util.LinkedHashMap<>();
        for (ScannedReceipt r : receiptRepository.findByUsernameAndYearMonth(username, ym)) {
            byCat.merge(r.getCategory() != null ? r.getCategory() : "Other", r.getAmount(), Double::sum);
        }
        for (java.util.Map.Entry<String, Double> e : byCat.entrySet()) {
            if (e.getValue() > 0) {
                // Append "(receipts)" only if the category already exists in the budget pie
                String lbl = pieLabels.contains(e.getKey())
                        ? e.getKey() + " (receipts)" : e.getKey();
                pieLabels.add(lbl);
                pieData.add(e.getValue());
            }
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        String username = (principal != null) ? principal.getName() : "User";
        model.addAttribute("username", username);
        model.addAttribute("currentDate",
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));

        Budget budget = budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null);

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

            // Actual Budget — receipts from Actual Expense page
            double actualExpenses    = receiptTotal(username);
            double actualSavings     = income - actualExpenses;
            double actualSavingsRate = (income == 0) ? 0 : Math.max((actualSavings / income) * 100, 0);
            double actualPercent     = (income == 0) ? 0 : Math.min((actualExpenses / income) * 100, 100);
            String actualStatus      = actualSavings < 0 ? "overspending"
                                     : (actualSavings < income * 0.2 ? "low" : "healthy");

            // Pie chart data built from actual receipts
            java.util.List<String> actualPieLabels = new java.util.ArrayList<>();
            java.util.List<Double> actualPieData   = new java.util.ArrayList<>();
            addReceiptsToPie(username, actualPieLabels, actualPieData);

            model.addAttribute("hasBudget",        true);
            model.addAttribute("income",           String.format("%.2f", income));
            model.addAttribute("expenses",         String.format("%.2f", expenses));
            model.addAttribute("savings",          String.format("%.2f", savings));
            model.addAttribute("savingsRate",      String.format("%.0f", savingsRate));
            model.addAttribute("percent",          String.format("%.0f", percent));
            model.addAttribute("status",           status);
            model.addAttribute("pieLabels",        pieLabels);
            model.addAttribute("pieData",          pieData);
            model.addAttribute("actualExpenses",    String.format("%.2f", actualExpenses));
            model.addAttribute("actualSavings",     String.format("%.2f", actualSavings));
            model.addAttribute("actualSavingsRate", String.format("%.0f", actualSavingsRate));
            model.addAttribute("actualPercent",     String.format("%.0f", actualPercent));
            model.addAttribute("actualStatus",      actualStatus);
            model.addAttribute("actualPieLabels",   actualPieLabels);
            model.addAttribute("actualPieData",     actualPieData);
        } else {
            model.addAttribute("hasBudget", false);
            model.addAttribute("status", "none");
            model.addAttribute("actualStatus", "none");
        }

        return "Dashboard";
    }

    @GetMapping("/spending-visualization")
    public String spendingVisualization(Principal principal, Model model) {
        String username = (principal != null) ? principal.getName() : null;
        Budget budget = (username != null)
            ? budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null)
            : null;

        if (budget != null) {
            double income = budget.getMonthlyIncome() + budget.getOtherIncome();

            // Use only actual expenses from the Actual Expense page
            java.util.List<String> pieLabels = new java.util.ArrayList<>();
            java.util.List<Double> pieData   = new java.util.ArrayList<>();
            addReceiptsToPie(username, pieLabels, pieData);
            double totalExpenses = receiptTotal(username);

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
    public String tracker(Principal principal, Model model) {
        String username = (principal != null) ? principal.getName() : null;
        Budget budget = (username != null)
            ? budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null)
            : null;

        if (budget != null) {
            double income   = budget.getMonthlyIncome() + budget.getOtherIncome();
            double expenses = receiptTotal(username);

            double totalSaved = Math.max(income - expenses, 0);

            model.addAttribute("hasBudget", true);
            model.addAttribute("serverTotalSaved", String.format("%.2f", totalSaved));
        } else {
            model.addAttribute("hasBudget", false);
            model.addAttribute("serverTotalSaved", "0.00");
        }

        model.addAttribute("trackerUsername", username != null ? username : "guest");
        return "Trackerpage";
    }

    @GetMapping("/budget")
    public String budget(Principal principal, Model model) {
        String currentMonth = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        model.addAttribute("currentMonth", currentMonth);

        if (principal != null) {
            Budget existing = budgetRepository
                    .findTopByUsernameOrderByIdDesc(principal.getName()).orElse(null);
            if (existing != null) {
                model.addAttribute("budget", existing);
                model.addAttribute("varTitles",  existing.getVariableTitle());
                model.addAttribute("varAmounts", existing.getVariableAmount());
            }
        }
        return "BudgetSet";
    }

@GetMapping("/predictive-cash-flow")
public String predictiveCashFlow(Principal principal, Model model) {
    String username = (principal != null) ? principal.getName() : null;
    Budget budget = (username != null)
        ? budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null)
        : null;

    if (budget != null) {
        double income   = budget.getMonthlyIncome() + budget.getOtherIncome();
        double expenses = receiptTotal(username);

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
public String alerts(Principal principal, Model model) {
    // Always set safe defaults first so the template never renders with missing attributes
    model.addAttribute("hasBudget", false);
    model.addAttribute("status", "none");
    model.addAttribute("varTitles",  Collections.emptyList());
    model.addAttribute("varAmounts", Collections.emptyList());
    model.addAttribute("currentMonth",
        LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));

    try {
        String username = (principal != null) ? principal.getName() : null;
        Budget budget = (username != null)
            ? budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null)
            : null;

        if (budget != null) {
            double income   = budget.getMonthlyIncome() + budget.getOtherIncome();
            double expenses = receiptTotal(username);

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
            model.addAttribute("varTitles",    budget.getVariableTitle()  != null ? budget.getVariableTitle()  : Collections.emptyList());
            model.addAttribute("varAmounts",   budget.getVariableAmount() != null ? budget.getVariableAmount() : Collections.emptyList());
        }
    } catch (Exception e) {
        // Keep safe defaults already set above; page renders without budget data
    }

    return "AlertsNotifications";
}

@GetMapping("/insights")
public String insights(Principal principal, Model model) {
    model.addAttribute("income",    "0.00");
    model.addAttribute("expenses",  "0.00");
    model.addAttribute("remaining", "0.00");
    model.addAttribute("percent",   "0");
    model.addAttribute("status",    "none");

    try {
        String username = (principal != null) ? principal.getName() : null;
        Budget budget = (username != null)
            ? budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null)
            : null;

        if (budget != null) {
            double income   = budget.getMonthlyIncome() + budget.getOtherIncome();
            double expenses = (username != null) ? receiptTotal(username) : 0;
            double remaining = income - expenses;
            double percent  = (income == 0) ? 0 : Math.min((expenses / income) * 100, 100);

            String status;
            if (remaining < 0)                 status = "overspending";
            else if (remaining < income * 0.2) status = "low";
            else                               status = "healthy";

            model.addAttribute("income",    String.format("%.2f", income));
            model.addAttribute("expenses",  String.format("%.2f", expenses));
            model.addAttribute("remaining", String.format("%.2f", remaining));
            model.addAttribute("percent",   String.format("%.0f", percent));
            model.addAttribute("status",    status);
        }
    } catch (Exception e) {
        // Safe defaults already set above; page renders without budget data
    }

    return "Insightspage";
}
}
