package com.finvision.controller;


import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.finvision.model.Budget;
import com.finvision.model.Bill;
import com.finvision.model.ScannedReceipt;
import com.finvision.repository.BudgetRepository;
import com.finvision.repository.BillRepository;
import com.finvision.repository.ScannedReceiptRepository;
import com.finvision.service.BillService;

@Controller
public class DashboardController {

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private ScannedReceiptRepository receiptRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillService billService;

    private static final DateTimeFormatter UI_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy");

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

    private String formatDueDate(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            return LocalDate.parse(raw).format(UI_DATE);
        } catch (DateTimeParseException ignored) {
            return raw;
        }
    }

    private Budget resolveBudgetForCurrentMonth(String username) {
        String month = LocalDate.now().format(MONTH_FMT);
        return budgetRepository.findByUsernameAndMonth(username, month)
                .orElseGet(() -> budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null));
    }

    private double billTotalForCurrentMonth(String username) {
        if (username == null || username.isBlank()) return 0;
        LocalDate now = LocalDate.now();
        return billRepository.findByUsernameOrderByDueDateAsc(username).stream()
                .filter(b -> b.getDueDate() != null
                        && b.getDueDate().getYear() == now.getYear()
                        && b.getDueDate().getMonth() == now.getMonth())
                .mapToDouble(Bill::getAmount)
                .sum();
    }

    private String bucketForBillCategory(String category) {
        String c = (category == null) ? "" : category.trim().toLowerCase();
        if (c.contains("utility") || c.contains("water") || c.contains("electric")
                || c.contains("internet") || c.contains("gas") || c.contains("power")) return "Utilities";
        if (c.contains("rent") || c.contains("housing") || c.contains("mortgage")) return "Rent";
        if (c.contains("insurance")) return "Insurance";
        if (c.contains("grocery") || c.contains("food")) return "Groceries";
        if (c.contains("subscription") || c.contains("streaming")) return "Subscriptions";
        if (c.isBlank()) return "Other Bills";
        return category.trim();
    }

    private void addBillsToPieAndGetTotal(String username,
            java.util.List<String> pieLabels, java.util.List<Double> pieData, double[] totalOut) {
        if (username == null || username.isBlank()) {
            totalOut[0] = 0;
            return;
        }
        LocalDate now = LocalDate.now();
        java.util.Map<String, Double> byCat = new java.util.LinkedHashMap<>();
        double sum = 0;
        for (Bill b : billRepository.findByUsernameOrderByDueDateAsc(username)) {
            if (b.getDueDate() == null
                    || b.getDueDate().getYear() != now.getYear()
                    || b.getDueDate().getMonth() != now.getMonth()) continue;
            double amt = b.getAmount();
            if (amt <= 0) continue;
            sum += amt;
            byCat.merge(bucketForBillCategory(b.getCategory()), amt, Double::sum);
        }
        for (java.util.Map.Entry<String, Double> e : byCat.entrySet()) {
            if (e.getValue() > 0) {
                String label = e.getKey();
                int idx = pieLabels.indexOf(label);
                if (idx >= 0) pieData.set(idx, pieData.get(idx) + e.getValue());
                else {
                    pieLabels.add(label);
                    pieData.add(e.getValue());
                }
            }
        }
        totalOut[0] = sum;
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        String username = (principal != null) ? principal.getName() : "User";
        model.addAttribute("username", username);
        model.addAttribute("currentDate",
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));

        Budget budget = resolveBudgetForCurrentMonth(username);

        if (budget != null) {
            double income   = budget.getMonthlyIncome() + budget.getOtherIncome();
            double expenses = budget.getRent() + budget.getUtilities() +
                              budget.getInsurance() + budget.getGroceries() +
                              budget.getSubscriptions();

            java.util.List<Double> varAmounts = budget.getVariableAmount();
            if (varAmounts != null) {
                for (Double amt : varAmounts) { if (amt != null && amt > 0) expenses += amt; }
            }

            // Add scanned receipts + bill manager amounts for this month
            expenses += receiptTotal(username);
            expenses += billTotalForCurrentMonth(username);

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
            addReceiptsToPie(username, pieLabels, pieData);
            double[] billTotal = new double[1];
            addBillsToPieAndGetTotal(username, pieLabels, pieData, billTotal);

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
    public String spendingVisualization(Principal principal, Model model) {
        String username = (principal != null) ? principal.getName() : null;
        Budget budget = (username != null) ? resolveBudgetForCurrentMonth(username) : null;

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

            // Add receipt + bill breakdown for this month
            addReceiptsToPie(username, pieLabels, pieData);
            totalExpenses += receiptTotal(username);
            double[] billTotal = new double[1];
            addBillsToPieAndGetTotal(username, pieLabels, pieData, billTotal);
            totalExpenses += billTotal[0];

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
    public String budget(Principal principal, Model model) {
        String currentMonth = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        model.addAttribute("currentMonth", currentMonth);

        if (principal != null) {
            Budget existing = resolveBudgetForCurrentMonth(principal.getName());
            if (existing != null) {
                model.addAttribute("budget", existing);
                model.addAttribute("varTitles",  existing.getVariableTitle());
                model.addAttribute("varAmounts", existing.getVariableAmount());
                model.addAttribute("varDueDates", existing.getVariableDueDate());
            }
        }
        return "BudgetSet";
    }

@GetMapping("/predictive-cash-flow")
public String predictiveCashFlow(Principal principal, Model model) {
    String username = (principal != null) ? principal.getName() : null;
    Budget budget = (username != null) ? resolveBudgetForCurrentMonth(username) : null;

    if (budget != null) {
        double income   = budget.getMonthlyIncome() + budget.getOtherIncome();
        double expenses = budget.getRent() + budget.getUtilities() +
                          budget.getInsurance() + budget.getGroceries() +
                          budget.getSubscriptions();

        java.util.List<Double> varAmounts = budget.getVariableAmount();
        if (varAmounts != null) {
            for (Double amt : varAmounts) { if (amt != null && amt > 0) expenses += amt; }
        }

        // Add scanned receipts + bill manager amounts for this month
        expenses += receiptTotal(username);
        expenses += billTotalForCurrentMonth(username);

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
public String alerts(@RequestParam(required = false) String editBillId,
                     Principal principal, Model model) {
    String username = (principal != null) ? principal.getName() : null;
    Budget budget = (username != null) ? resolveBudgetForCurrentMonth(username) : null;
    BillService.AlertsBillView billView = (username != null)
            ? billService.buildAlertsBillView(username)
            : new BillService.AlertsBillView(
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    0, 0, 0, "$0.00", "$0.00"
            );

    Bill editingBill = null;
    if (username != null && editBillId != null && !editBillId.isBlank()) {
        editingBill = billRepository.findByIdAndUsername(editBillId, username).orElse(null);
    }

    if (budget != null) {
        double income   = budget.getMonthlyIncome() + budget.getOtherIncome();
        double expenses = budget.getRent() + budget.getUtilities() +
                          budget.getInsurance() + budget.getGroceries() +
                          budget.getSubscriptions();

        java.util.List<Double> varAmounts = budget.getVariableAmount();
        if (varAmounts != null) {
            for (Double amt : varAmounts) { if (amt != null && amt > 0) expenses += amt; }
        }

        // Add scanned receipts + bill manager amounts for this month
        expenses += receiptTotal(username);
        expenses += billTotalForCurrentMonth(username);

        double remaining = income - expenses;
        double percent   = (income == 0) ? 0 : Math.min((expenses / income) * 100, 100);
        String status    = remaining < 0 ? "overspending" : (remaining < income * 0.2 ? "low" : "healthy");

        model.addAttribute("hasBudget",  true);
        model.addAttribute("status",     status);
        model.addAttribute("income",     String.format("%.2f", income));
        model.addAttribute("expenses",   String.format("%.2f", expenses));
        model.addAttribute("remaining",  String.format("%.2f", remaining));
        model.addAttribute("percent",    String.format("%.0f", percent));
    } else {
        model.addAttribute("hasBudget", false);
        model.addAttribute("status", "none");
    }

    model.addAttribute("billView", billView);
    model.addAttribute("editingBill", editingBill);
    model.addAttribute("defaultReminderBefore", java.util.List.of(7, 3, 1, 0));
    model.addAttribute("defaultOverdueAfter", java.util.List.of(1, 3));
    model.addAttribute("activeBillReminders", username != null
            ? billService.buildActiveReminderItems(username)
            : java.util.Collections.emptyList());

    model.addAttribute("currentMonth",
        LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));

    return "AlertsNotifications";
}

@GetMapping("/insights")
public String insights(Principal principal, Model model) {
    String username = (principal != null) ? principal.getName() : null;
    Budget budget = (username != null) ? resolveBudgetForCurrentMonth(username) : null;

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

        java.util.List<Double> varAmounts = budget.getVariableAmount();
        if (varAmounts != null) {
            for (Double amt : varAmounts) {
                if (amt != null && amt > 0) expenses += amt;
            }
        }

        // Add scanned receipts + bill manager amounts for this month
        if (username != null) {
            expenses += receiptTotal(username);
            expenses += billTotalForCurrentMonth(username);
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
