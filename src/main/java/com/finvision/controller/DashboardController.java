package com.finvision.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.finvision.model.Budget;
import com.finvision.model.ScannedReceipt;
import com.finvision.repository.BudgetRepository;
import com.finvision.repository.ScannedReceiptRepository;

/**
 * Serves the main dashboard and all financial-overview pages in Finvision.
 *
 * <p>Pages handled:
 * <ul>
 *   <li>{@code /dashboard} — summary of budgeted vs. actual income/expenses</li>
 *   <li>{@code /spending-visualization} — pie-chart breakdown of actual spending</li>
 *   <li>{@code /tracker} — savings tracker with total saved this month</li>
 *   <li>{@code /budget} — budget entry form (pre-filled if a budget exists)</li>
 *   <li>{@code /budget-history} — month-by-month budget vs. actual history</li>
 *   <li>{@code /predictive-cash-flow} — 6-month projected cash-flow forecast</li>
 *   <li>{@code /alerts} — bill alerts and budget status overview</li>
 *   <li>{@code /insights} — spending insight summary</li>
 * </ul>
 */
@Controller
public class DashboardController {

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private ScannedReceiptRepository receiptRepository;

    /**
     * Returns the total dollar amount of all scanned receipts for the current
     * calendar month (format: {@code yyyy-MM}) for the given user.
     *
     * @param username the authenticated user's username
     * @return sum of receipt amounts for the current month
     */
    private double receiptTotal(String username) {
        String ym = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return receiptRepository.findByUsernameAndYearMonth(username, ym)
                .stream().mapToDouble(ScannedReceipt::getAmount).sum();
    }

    /**
     * Appends per-category receipt totals for the current month to the provided
     * pie-chart label and data lists. If a category name already exists in
     * {@code pieLabels} (from the budget breakdown), it is suffixed with
     * {@code " (receipts)"} to avoid duplicate labels.
     *
     * @param username  the authenticated user's username
     * @param pieLabels mutable list of chart labels to append to
     * @param pieData   mutable list of chart amounts to append to
     */
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
                        ? e.getKey() + " (receipts)"
                        : e.getKey();
                pieLabels.add(lbl);
                pieData.add(e.getValue());
            }
        }
    }

    /**
     * Renders the main dashboard with both budgeted and actual financial summaries.
     *
     * <p>When a budget exists, populates income, expenses, savings, savings rate,
     * spending percentage, status label, and pie-chart data for both the planned
     * budget and actual (receipt-based) spending.</p>
     *
     * @param principal the currently authenticated user
     * @param model     Spring MVC model
     * @return the {@code Dashboard} template
     */
    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        String username = (principal != null) ? principal.getName() : "User";
        model.addAttribute("username", username);
        model.addAttribute("currentDate",
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));

        Budget budget = budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null);

        if (budget != null) {
            double income = budget.getMonthlyIncome() + budget.getOtherIncome();
            double expenses = budget.getRent() + budget.getUtilities() +
                    budget.getInsurance() + budget.getGroceries() +
                    budget.getSubscriptions();

            java.util.List<Double> varAmounts = budget.getVariableAmount();
            if (varAmounts != null) {
                for (Double amt : varAmounts) {
                    if (amt != null && amt > 0)
                        expenses += amt;
                }
            }

            double savings = income - expenses;
            double savingsRate = (income == 0) ? 0 : Math.max((savings / income) * 100, 0);
            double percent = (income == 0) ? 0 : Math.min((expenses / income) * 100, 100);
            String status = savings < 0 ? "overspending" : (savings < income * 0.2 ? "low" : "healthy");

            java.util.List<String> pieLabels = new java.util.ArrayList<>();
            java.util.List<Double> pieData = new java.util.ArrayList<>();
            if (budget.getRent() > 0) {
                pieLabels.add("Rent");
                pieData.add(budget.getRent());
            }
            if (budget.getUtilities() > 0) {
                pieLabels.add("Utilities");
                pieData.add(budget.getUtilities());
            }
            if (budget.getInsurance() > 0) {
                pieLabels.add("Insurance");
                pieData.add(budget.getInsurance());
            }
            if (budget.getGroceries() > 0) {
                pieLabels.add("Groceries");
                pieData.add(budget.getGroceries());
            }
            if (budget.getSubscriptions() > 0) {
                pieLabels.add("Subscriptions");
                pieData.add(budget.getSubscriptions());
            }
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
            double actualExpenses = receiptTotal(username);
            double actualSavings = income - actualExpenses;
            double actualSavingsRate = (income == 0) ? 0 : Math.max((actualSavings / income) * 100, 0);
            double actualPercent = (income == 0) ? 0 : Math.min((actualExpenses / income) * 100, 100);
            String actualStatus = actualSavings < 0 ? "overspending"
                    : (actualSavings < income * 0.2 ? "low" : "healthy");

            // Pie chart data built from actual receipts
            java.util.List<String> actualPieLabels = new java.util.ArrayList<>();
            java.util.List<Double> actualPieData = new java.util.ArrayList<>();
            addReceiptsToPie(username, actualPieLabels, actualPieData);

            model.addAttribute("hasBudget", true);
            model.addAttribute("income", String.format("%.2f", income));
            model.addAttribute("expenses", String.format("%.2f", expenses));
            model.addAttribute("savings", String.format("%.2f", savings));
            model.addAttribute("savingsRate", String.format("%.0f", savingsRate));
            model.addAttribute("percent", String.format("%.0f", percent));
            model.addAttribute("status", status);
            model.addAttribute("pieLabels", pieLabels);
            model.addAttribute("pieData", pieData);
            model.addAttribute("actualExpenses", String.format("%.2f", actualExpenses));
            model.addAttribute("actualSavings", String.format("%.2f", actualSavings));
            model.addAttribute("actualSavingsRate", String.format("%.0f", actualSavingsRate));
            model.addAttribute("actualPercent", String.format("%.0f", actualPercent));
            model.addAttribute("actualStatus", actualStatus);
            model.addAttribute("actualPieLabels", actualPieLabels);
            model.addAttribute("actualPieData", actualPieData);
        } else {
            model.addAttribute("hasBudget", false);
            model.addAttribute("status", "none");
            model.addAttribute("actualStatus", "none");
        }

        return "Dashboard";
    }

    /**
     * Renders the Spending Visualization page with a pie chart of actual expenses.
     *
     * @param principal the currently authenticated user
     * @param model     Spring MVC model
     * @return the {@code SpendingVisualization} template
     */
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
            java.util.List<Double> pieData = new java.util.ArrayList<>();
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

    /**
     * Renders the Savings Tracker page with this month's total amount saved
     * (income minus actual expenses, floored at zero).
     *
     * @param principal the currently authenticated user
     * @param model     Spring MVC model
     * @return the {@code Trackerpage} template
     */
    @GetMapping("/tracker")
    public String tracker(Principal principal, Model model) {
        String username = (principal != null) ? principal.getName() : null;
        Budget budget = (username != null)
                ? budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null)
                : null;

        if (budget != null) {
            double income = budget.getMonthlyIncome() + budget.getOtherIncome();
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

    /**
     * Displays the Budget Set form, pre-filled with the existing budget for
     * the current month if one exists.
     *
     * @param principal the currently authenticated user
     * @param model     Spring MVC model
     * @return the {@code BudgetSet} template
     */
    @GetMapping("/budget")
    public String budget(Principal principal, Model model) {
        String currentMonth = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        model.addAttribute("currentMonth", currentMonth);

        if (principal != null) {
            Budget existing = budgetRepository
                    .findByUsernameAndMonth(principal.getName(), currentMonth).orElse(null);
            if (existing != null) {
                model.addAttribute("budget", existing);
                model.addAttribute("varTitles", existing.getVariableTitle());
                model.addAttribute("varAmounts", existing.getVariableAmount());
            }
        }
        return "BudgetSet";
    }

    /**
     * Renders the Budget History page showing month-by-month budgeted vs. actual
     * spending, including chart data for a bar/line visualization.
     *
     * @param principal the currently authenticated user
     * @param model     Spring MVC model
     * @return the {@code BudgetHistory} template
     */
    @GetMapping("/budget-history")
    public String budgetHistory(Principal principal, Model model) {
        String username = (principal != null) ? principal.getName() : null;

        List<Budget> budgets = (username != null)
                ? budgetRepository.findByUsernameOrderByIdDesc(username)
                : Collections.emptyList();

        DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
        DateTimeFormatter ymFmt = DateTimeFormatter.ofPattern("yyyy-MM");

        List<Map<String, Object>> history = new ArrayList<>();
        List<String> chartMonths = new ArrayList<>();
        List<String> chartBudgeted = new ArrayList<>();
        List<String> chartActual = new ArrayList<>();

        for (Budget b : budgets) {
            String yearMonth;
            try {
                YearMonth ym = YearMonth.parse(b.getMonth(), displayFmt);
                yearMonth = ym.format(ymFmt);
            } catch (Exception e) {
                continue;
            }

            double budgetedExpenses = b.getRent() + b.getUtilities() + b.getInsurance()
                    + b.getGroceries() + b.getSubscriptions();
            List<Double> varAmounts = b.getVariableAmount();
            if (varAmounts != null) {
                for (Double amt : varAmounts) {
                    if (amt != null && amt > 0) budgetedExpenses += amt;
                }
            }

            double actualExpenses = (username != null)
                    ? receiptRepository.findByUsernameAndYearMonth(username, yearMonth)
                            .stream().mapToDouble(ScannedReceipt::getAmount).sum()
                    : 0;

            double income = b.getMonthlyIncome() + b.getOtherIncome();
            double diff = budgetedExpenses - actualExpenses;
            String diffSign = diff >= 0 ? "under" : "over";
            String status;
            if (actualExpenses > budgetedExpenses) status = "over";
            else if (actualExpenses > budgetedExpenses * 0.9) status = "near";
            else status = "under";

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("month", b.getMonth());
            entry.put("income", String.format("%.2f", income));
            entry.put("budgeted", String.format("%.2f", budgetedExpenses));
            entry.put("actual", String.format("%.2f", actualExpenses));
            entry.put("difference", String.format("%.2f", Math.abs(diff)));
            entry.put("diffSign", diffSign);
            entry.put("status", status);
            history.add(entry);

            chartMonths.add(b.getMonth());
            chartBudgeted.add(String.format("%.2f", budgetedExpenses));
            chartActual.add(String.format("%.2f", actualExpenses));
        }

        Collections.reverse(history);
        Collections.reverse(chartMonths);
        Collections.reverse(chartBudgeted);
        Collections.reverse(chartActual);

        model.addAttribute("history", history);
        model.addAttribute("hasHistory", !history.isEmpty());
        model.addAttribute("chartMonths", chartMonths);
        model.addAttribute("chartBudgeted", chartBudgeted);
        model.addAttribute("chartActual", chartActual);
        return "BudgetHistory";
    }

    /**
     * Renders the Predictive Cash Flow page with a 6-month rolling forecast
     * based on the current month's income and actual spending rate.
     *
     * @param principal the currently authenticated user
     * @param model     Spring MVC model
     * @return the {@code PredictiveCashFlow} template
     */
    @GetMapping("/predictive-cash-flow")
    public String predictiveCashFlow(Principal principal, Model model) {
        String username = (principal != null) ? principal.getName() : null;
        Budget budget = (username != null)
                ? budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null)
                : null;

        if (budget != null) {
            double income = budget.getMonthlyIncome() + budget.getOtherIncome();
            double expenses = receiptTotal(username);

            double monthlySavings = income - expenses;
            double savingsRate = (income == 0) ? 0 : (monthlySavings / income) * 100;

            // Build 6-month forecast (labels + cumulative balances)
            java.util.List<String> monthLabels = new java.util.ArrayList<>();
            java.util.List<String> projectedBalances = new java.util.ArrayList<>();
            java.util.List<String> monthlyIncomes = new java.util.ArrayList<>();
            java.util.List<String> monthlyExpenses = new java.util.ArrayList<>();

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

            model.addAttribute("hasBudget", true);
            model.addAttribute("income", String.format("%.2f", income));
            model.addAttribute("expenses", String.format("%.2f", expenses));
            model.addAttribute("monthlySavings", String.format("%.2f", monthlySavings));
            model.addAttribute("savingsRate", String.format("%.0f", savingsRate));
            model.addAttribute("sixMonthTotal", String.format("%.2f", cumulative));
            model.addAttribute("trajectory", trajectory);
            model.addAttribute("monthLabels", monthLabels);
            model.addAttribute("projectedBalances", projectedBalances);
            model.addAttribute("monthlyIncomes", monthlyIncomes);
            model.addAttribute("monthlyExpenses", monthlyExpenses);
        } else {
            model.addAttribute("hasBudget", false);
            model.addAttribute("trajectory", "none");
        }

        model.addAttribute("currentMonth",
                LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));

        return "PredictiveCashFlow";
    }

    /**
     * Renders the Alerts &amp; Notifications page with budget status and bill list.
     *
     * <p>Safe defaults are set before fetching data so the template never
     * renders with missing attributes even if no budget exists.</p>
     *
     * @param principal the currently authenticated user
     * @param model     Spring MVC model
     * @return the {@code AlertsNotifications} template
     */
    @GetMapping("/alerts")
    public String alerts(Principal principal, Model model) {
        // Always set safe defaults first so the template never renders with missing
        // attributes
        model.addAttribute("hasBudget", false);
        model.addAttribute("status", "none");
        model.addAttribute("varTitles", Collections.emptyList());
        model.addAttribute("varAmounts", Collections.emptyList());
        model.addAttribute("currentMonth",
                LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));

        try {
            String username = (principal != null) ? principal.getName() : null;
            Budget budget = (username != null)
                    ? budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null)
                    : null;

            if (budget != null) {
                double income = budget.getMonthlyIncome() + budget.getOtherIncome();
                double expenses = receiptTotal(username);

                double remaining = income - expenses;
                double percent = (income == 0) ? 0 : Math.min((expenses / income) * 100, 100);
                String status = remaining < 0 ? "overspending" : (remaining < income * 0.2 ? "low" : "healthy");

                model.addAttribute("hasBudget", true);
                model.addAttribute("status", status);
                model.addAttribute("income", String.format("%.2f", income));
                model.addAttribute("expenses", String.format("%.2f", expenses));
                model.addAttribute("remaining", String.format("%.2f", remaining));
                model.addAttribute("percent", String.format("%.0f", percent));
                model.addAttribute("rent", String.format("%.2f", budget.getRent()));
                model.addAttribute("utilities", String.format("%.2f", budget.getUtilities()));
                model.addAttribute("insurance", String.format("%.2f", budget.getInsurance()));
                model.addAttribute("groceries", String.format("%.2f", budget.getGroceries()));
                model.addAttribute("subscriptions", String.format("%.2f", budget.getSubscriptions()));
                model.addAttribute("varTitles",
                        budget.getVariableTitle() != null ? budget.getVariableTitle() : Collections.emptyList());
                model.addAttribute("varAmounts",
                        budget.getVariableAmount() != null ? budget.getVariableAmount() : Collections.emptyList());
            }
        } catch (Exception e) {
            // Keep safe defaults already set above; page renders without budget data
        }

        return "AlertsNotifications";
    }

    /**
     * Renders the Insights page with a high-level spending summary.
     *
     * @param principal the currently authenticated user
     * @param model     Spring MVC model
     * @return the {@code Insightspage} template
     */
    @GetMapping("/insights")
    public String insights(Principal principal, Model model) {
        model.addAttribute("income", "0.00");
        model.addAttribute("expenses", "0.00");
        model.addAttribute("remaining", "0.00");
        model.addAttribute("percent", "0");
        model.addAttribute("status", "none");

        try {
            String username = (principal != null) ? principal.getName() : null;
            Budget budget = (username != null)
                    ? budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null)
                    : null;

            if (budget != null) {
                double income = budget.getMonthlyIncome() + budget.getOtherIncome();
                double expenses = (username != null) ? receiptTotal(username) : 0;
                double remaining = income - expenses;
                double percent = (income == 0) ? 0 : Math.min((expenses / income) * 100, 100);

                String status;
                if (remaining < 0)
                    status = "overspending";
                else if (remaining < income * 0.2)
                    status = "low";
                else
                    status = "healthy";

                model.addAttribute("income", String.format("%.2f", income));
                model.addAttribute("expenses", String.format("%.2f", expenses));
                model.addAttribute("remaining", String.format("%.2f", remaining));
                model.addAttribute("percent", String.format("%.0f", percent));
                model.addAttribute("status", status);
            }
        } catch (Exception e) {
            // Safe defaults already set above; page renders without budget data
        }

        return "Insightspage";
    }
}
