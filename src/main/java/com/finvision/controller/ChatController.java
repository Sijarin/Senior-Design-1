package com.finvision.controller;

import com.finvision.model.Budget;
import com.finvision.model.ScannedReceipt;
import com.finvision.model.User;
import com.finvision.repository.BudgetRepository;
import com.finvision.repository.ScannedReceiptRepository;
import com.finvision.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
public class ChatController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private ScannedReceiptRepository receiptRepository;

    @PostMapping("/chat")
    @ResponseBody
    public Map<String, String> chat(@RequestBody Map<String, String> body, Principal principal) {
        String msg = body.getOrDefault("message", "").toLowerCase().trim();
        User user = null;
        Budget budget = null;
        List<ScannedReceipt> receipts = new ArrayList<>();

        if (principal != null) {
            user   = userRepository.findByUsername(principal.getName()).orElse(null);
            budget = budgetRepository.findTopByUsernameOrderByIdDesc(principal.getName()).orElse(null);
            String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            receipts = receiptRepository.findByUsernameAndYearMonth(principal.getName(), yearMonth);
        }

        Map<String, String> result = new HashMap<>();
        result.put("response", generateResponse(msg, user, budget, receipts));
        return result;
    }

    private String generateResponse(String msg, User user, Budget budget, List<ScannedReceipt> receipts) {
        String name = getDisplayName(user);

        // ── Compute set-budget stats ──
        double income = 0, expenses = 0, savings = 0, savingsRate = 0;
        Map<String, Double> categories = new LinkedHashMap<>();
        String status = "none";

        if (budget != null) {
            income = budget.getMonthlyIncome() + budget.getOtherIncome();
            if (budget.getRent()          > 0) { expenses += budget.getRent();          categories.put("Rent",          budget.getRent()); }
            if (budget.getUtilities()     > 0) { expenses += budget.getUtilities();     categories.put("Utilities",     budget.getUtilities()); }
            if (budget.getInsurance()     > 0) { expenses += budget.getInsurance();     categories.put("Insurance",     budget.getInsurance()); }
            if (budget.getGroceries()     > 0) { expenses += budget.getGroceries();     categories.put("Groceries",     budget.getGroceries()); }
            if (budget.getSubscriptions() > 0) { expenses += budget.getSubscriptions(); categories.put("Subscriptions", budget.getSubscriptions()); }

            List<String> vt = budget.getVariableTitle();
            List<Double> va = budget.getVariableAmount();
            if (vt != null && va != null) {
                for (int i = 0; i < Math.min(vt.size(), va.size()); i++) {
                    if (va.get(i) != null && va.get(i) > 0) {
                        expenses += va.get(i);
                        categories.put(vt.get(i), va.get(i));
                    }
                }
            }
            savings     = income - expenses;
            savingsRate = income > 0 ? (savings / income) * 100 : 0;
            status      = savings < 0 ? "overspending" : (savings < income * 0.2 ? "low" : "healthy");
        }

        // ── Compute actual expense stats (from receipts) ──
        double actualTotal = receipts.stream().mapToDouble(ScannedReceipt::getAmount).sum();
        Map<String, Double> actualCategories = new LinkedHashMap<>();
        for (ScannedReceipt r : receipts) {
            String cat = (r.getCategory() != null && !r.getCategory().isBlank()) ? r.getCategory() : "Other";
            actualCategories.merge(cat, r.getAmount(), Double::sum);
        }

        final double expTotal = expenses;
        String highestCat = categories.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        String highestActualCat = actualCategories.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

        // ── Greetings ──
        if (matches(msg, "hello", "hi ", "hey ", "good morning", "good evening", "howdy", "what's up", "sup")) {
            return "Hello " + name + "! \uD83D\uDC4B I'm FinVision AI, your personal finance assistant.\n\nI can help with:\n\u2022 Budget & actual spending insights\n\u2022 Account & profile information\n\u2022 Expense categories & comparisons\n\u2022 Savings analysis & financial tips\n\nType 'help' for all commands, or just ask anything!";
        }

        // ── Help ──
        if (matches(msg, "help", "what can you do", "commands", "capabilities", "menu")) {
            return "Here's what I can answer:\n\n"
                + "\uD83D\uDCCA Set Budget \u2014 'Show my set budget details'\n"
                + "\uD83D\uDCB0 Income \u2014 'What\u2019s my income?'\n"
                + "\uD83E\uDDFE Actual Expenses \u2014 'What did I actually spend?'\n"
                + "\uD83D\uDCC2 Categories \u2014 'Show actual categories'\n"
                + "\uD83C\uDD9A Compare \u2014 'Budget vs actual'\n"
                + "\uD83D\uDC37 Savings \u2014 'How much am I saving?'\n"
                + "\u2764\uFE0F Health \u2014 'How\u2019s my budget health?'\n"
                + "\uD83D\uDC64 Profile \u2014 'My name / email / username'\n"
                + "\uD83C\uDFE6 Banking \u2014 'My account number'\n"
                + "\uD83D\uDCA1 Tips \u2014 'Give me financial advice'\n"
                + "\uD83D\uDCC8 Forecast \u2014 'Show my cash flow'\n"
                + "\uD83C\uDFAF Goals \u2014 'Help me set financial goals'\n"
                + "\uD83D\uDCCB Summary \u2014 'Show all my data'\n\n"
                + "\uD83C\uDFA4 You can also use the microphone for voice commands!";
        }

        // ── Email ──
        if (matches(msg, "email", "my email", "email address", "what is my email", "what's my email")) {
            if (user == null) return "Please log in to view profile info.";
            if (user.getEmail() == null || user.getEmail().isBlank())
                return "No email address saved on your account.";
            return "\uD83D\uDCE7 Your email address is: " + user.getEmail();
        }

        // ── Full name ──
        if (matches(msg, "my full name", "what is my name", "what's my name", "full name", "my name")) {
            if (user == null) return "Please log in to view profile info.";
            if (user.getName() == null || user.getName().isBlank())
                return "No full name saved. It can be set during registration.";
            return "\uD83D\uDC64 Your full name is: " + user.getName();
        }

        // ── Username ──
        if (matches(msg, "username", "my username", "what is my username", "what's my username", "user name")) {
            if (user == null) return "Please log in to view profile info.";
            return "\uD83D\uDD11 Your username is: " + user.getUsername();
        }

        // ── Profile overview ──
        if (matches(msg, "who am i", "my profile", "my info", "about me", "profile info")) {
            if (user == null) return "You're not logged in.";
            StringBuilder r = new StringBuilder("\uD83D\uDC64 Your profile:\n");
            r.append("\u2022 Username: ").append(user.getUsername());
            if (user.getName()  != null && !user.getName().isBlank())  r.append("\n\u2022 Full Name: ").append(user.getName());
            if (user.getEmail() != null && !user.getEmail().isBlank()) r.append("\n\u2022 Email: ").append(user.getEmail());
            if (user.getAccountNumber() != null && !user.getAccountNumber().isBlank()) {
                String num = user.getAccountNumber();
                r.append("\n\u2022 Account: ****").append(num.substring(Math.max(0, num.length() - 4)));
            }
            return r.toString();
        }

        // ── Individual budget category questions ──
        if (matches(msg, "my rent", "how much is my rent", "rent budget", "rent expense")) {
            if (budget == null) return noBudget();
            return budget.getRent() > 0
                ? String.format("\uD83C\uDFE0 Your budgeted rent is $%.2f/month.", budget.getRent())
                : "Rent is not set in your budget. Visit 'Set Budget' to add it.";
        }
        if (matches(msg, "my utilities", "utility budget", "electricity", "water bill", "internet bill")) {
            if (budget == null) return noBudget();
            return budget.getUtilities() > 0
                ? String.format("\uD83D\uDCA1 Your budgeted utilities is $%.2f/month.", budget.getUtilities())
                : "Utilities is not set in your budget.";
        }
        if (matches(msg, "my insurance", "insurance budget")) {
            if (budget == null) return noBudget();
            return budget.getInsurance() > 0
                ? String.format("\uD83D\uDEE1\uFE0F Your budgeted insurance is $%.2f/month.", budget.getInsurance())
                : "Insurance is not set in your budget.";
        }
        if (matches(msg, "my groceries", "grocery budget", "food budget")) {
            if (budget == null) return noBudget();
            return budget.getGroceries() > 0
                ? String.format("\uD83D\uDED2 Your budgeted groceries is $%.2f/month.", budget.getGroceries())
                : "Groceries is not set in your budget.";
        }
        if (matches(msg, "my subscriptions", "subscription budget", "streaming budget")) {
            if (budget == null) return noBudget();
            return budget.getSubscriptions() > 0
                ? String.format("\uD83D\uDCFA Your budgeted subscriptions is $%.2f/month.", budget.getSubscriptions())
                : "Subscriptions is not set in your budget.";
        }

        // ── Income ──
        if (matches(msg, "income", "earn", "salary", "how much do i make", "monthly income", "what i make", "my income")) {
            if (budget == null) return noBudget();
            StringBuilder r = new StringBuilder(String.format("\uD83D\uDCB0 Your total monthly income is $%.2f.", income));
            if (budget.getMonthlyIncome() > 0) r.append(String.format("\n\u2022 Primary income: $%.2f", budget.getMonthlyIncome()));
            if (budget.getOtherIncome()   > 0) r.append(String.format("\n\u2022 Other income: $%.2f",   budget.getOtherIncome()));
            return r.toString();
        }

        // ── Full Set Budget Overview (totals at top, full breakdown below) ──
        if (matches(msg, "set budget", "show my set budget", "set budget details", "budget details",
                    "full budget", "budget overview", "my budget details", "set budget expense",
                    "set expense", "planned expense", "my set expense", "what is set budget",
                    "budgeted expense", "show budget", "what is my budget", "my budget")) {
            if (budget == null) return noBudget();

            String month = (budget.getMonth() != null && !budget.getMonth().isBlank())
                    ? budget.getMonth() : "This Month";
            double pct = income > 0 ? (expenses / income) * 100 : 0;
            StringBuilder r = new StringBuilder();

            // ── Header ──
            r.append("\uD83D\uDCCA **Your Set Budget \u2014 ").append(month).append("**\n");
            r.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");

            // ── TOTALS at the top ──
            r.append("\uD83D\uDCB0 **TOTALS**\n");
            r.append(String.format("\u2022 Total Income:    **$%.2f**\n", income));
            r.append(String.format("\u2022 Total Expenses:  **$%.2f** (%.0f%% of income)\n", expenses, pct));
            if (savings >= 0) {
                r.append(String.format("\u2022 Net Savings:     **$%.2f** (%.0f%% savings rate)\n", savings, savingsRate));
            } else {
                r.append(String.format("\u2022 Over Budget by:  **$%.2f** \u26A0\uFE0F\n", Math.abs(savings)));
            }
            r.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

            // ── Income breakdown ──
            r.append("\uD83D\uDCE5 **INCOME**\n");
            if (budget.getMonthlyIncome() > 0) r.append(String.format("\u2022 Monthly Income:  $%.2f\n", budget.getMonthlyIncome()));
            if (budget.getOtherIncome()   > 0) r.append(String.format("\u2022 Other Income:    $%.2f\n", budget.getOtherIncome()));
            r.append("\n");

            // ── Fixed expenses ──
            boolean hasFixed = budget.getRent() > 0 || budget.getUtilities() > 0
                    || budget.getInsurance() > 0 || budget.getGroceries() > 0
                    || budget.getSubscriptions() > 0;
            if (hasFixed) {
                r.append("\uD83C\uDFE0 **FIXED EXPENSES**\n");
                if (budget.getRent()          > 0) r.append(String.format("\u2022 Rent:            $%.2f\n", budget.getRent()));
                if (budget.getUtilities()     > 0) r.append(String.format("\u2022 Utilities:       $%.2f\n", budget.getUtilities()));
                if (budget.getInsurance()     > 0) r.append(String.format("\u2022 Insurance:       $%.2f\n", budget.getInsurance()));
                if (budget.getGroceries()     > 0) r.append(String.format("\u2022 Groceries:       $%.2f\n", budget.getGroceries()));
                if (budget.getSubscriptions() > 0) r.append(String.format("\u2022 Subscriptions:   $%.2f\n", budget.getSubscriptions()));
                r.append("\n");
            }

            // ── Variable expenses ──
            List<String> vt = budget.getVariableTitle();
            List<Double> va = budget.getVariableAmount();
            boolean hasVariable = vt != null && va != null && !vt.isEmpty();
            if (hasVariable) {
                r.append("\uD83D\uDCDD **VARIABLE EXPENSES**\n");
                for (int i = 0; i < Math.min(vt.size(), va.size()); i++) {
                    if (va.get(i) != null && va.get(i) > 0 && vt.get(i) != null && !vt.get(i).isBlank()) {
                        r.append(String.format("\u2022 %-18s $%.2f\n", vt.get(i) + ":", va.get(i)));
                    }
                }
                r.append("\n");
            }

            // ── Health status ──
            String healthLine;
            switch (status) {
                case "healthy":
                    healthLine = "\u2764\uFE0F Budget Health: **Healthy** \u2014 Great job saving " + String.format("%.0f%%", savingsRate) + "!";
                    break;
                case "overspending":
                    healthLine = "\uD83D\uDEA8 Budget Health: **Overspending!** Expenses exceed income by $" + String.format("%.2f", Math.abs(savings)) + ".";
                    break;
                default:
                    healthLine = "\u26A0\uFE0F Budget Health: **Low Savings** \u2014 Try to reach 20% savings rate.";
            }
            r.append(healthLine).append("\n");
            r.append("\uD83D\uDC49 Visit **Set Budget** in the sidebar to update your budget.");

            return r.toString();
        }

        // ── Full Actual Expense Overview (totals at top, categories, all receipts) ──
        if (matches(msg, "actual expense", "actual spend", "actual cost", "what did i actually spend",
                    "real expense", "receipt total", "my receipts", "scanned receipt", "actual amount",
                    "how much did i spend", "actual categor", "receipt categor", "what did i spend on",
                    "actual breakdown", "show actual", "actual by category", "what are my actual")) {
            if (receipts.isEmpty())
                return "\uD83E\uDDFE No receipts scanned this month yet.\nUse the **Actual Expense** page in the sidebar to scan and log your expenses.";

            String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            StringBuilder r = new StringBuilder();

            // ── Header ──
            r.append("\uD83E\uDDFE **Your Actual Expenses \u2014 ").append(yearMonth).append("**\n");
            r.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");

            // ── TOTALS at the top ──
            r.append("\uD83D\uDCB0 **TOTALS**\n");
            r.append(String.format("\u2022 Total Spent:    **$%.2f** (%d receipt%s)\n",
                    actualTotal, receipts.size(), receipts.size() == 1 ? "" : "s"));
            if (budget != null) {
                double diff = expenses - actualTotal;
                r.append(String.format("\u2022 Set Budget:     $%.2f\n", expenses));
                if (diff >= 0)
                    r.append(String.format("\u2022 Difference:     \u2705 **$%.2f under budget** \u2014 great job!\n", diff));
                else
                    r.append(String.format("\u2022 Difference:     \u26A0\uFE0F **$%.2f over budget**\n", Math.abs(diff)));
            }
            r.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

            // ── By Category ──
            if (!actualCategories.isEmpty()) {
                r.append("\uD83D\uDCC2 **BY CATEGORY**\n");
                actualCategories.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .forEach(e -> r.append(String.format("\u2022 %-18s $%.2f  (%.0f%%)\n",
                        e.getKey() + ":", e.getValue(),
                        actualTotal > 0 ? e.getValue() / actualTotal * 100 : 0)));
                r.append("\n");
            }

            // ── All Receipts ──
            List<ScannedReceipt> sorted = new ArrayList<>(receipts);
            sorted.sort((a, b) -> {
                String da = a.getDateStr() != null ? a.getDateStr() : "";
                String db = b.getDateStr() != null ? b.getDateStr() : "";
                return db.compareTo(da);
            });
            r.append(String.format("\uD83E\uDDFE **ALL RECEIPTS (%d)**\n", sorted.size()));
            int show = Math.min(10, sorted.size());
            for (int i = 0; i < show; i++) {
                ScannedReceipt rec = sorted.get(i);
                String merchant = rec.getMerchantName() != null && !rec.getMerchantName().isBlank()
                        ? rec.getMerchantName() : "Unknown";
                String cat = rec.getCategory() != null && !rec.getCategory().isBlank()
                        ? rec.getCategory() : "Other";
                String date = rec.getDateStr() != null ? "  " + rec.getDateStr() : "";
                r.append(String.format("\u2022 %-20s $%.2f  [%s]%s\n", merchant, rec.getAmount(), cat, date));
            }
            if (sorted.size() > 10)
                r.append("... and ").append(sorted.size() - 10).append(" more receipts.\n");

            r.append("\n\uD83D\uDC49 Visit **Actual Expense** in the sidebar to manage your receipts.");
            return r.toString();
        }

        // ── Expenses (general) ──
        if (matches(msg, "expense", "spend", "how much do i spend", "total expense", "my expenses", "costs", "what i spend")) {
            if (budget == null) return noBudget();
            double pct = income > 0 ? (expenses / income) * 100 : 0;
            StringBuilder r = new StringBuilder(String.format("\uD83D\uDCCA Set budget — Total: $%.2f/month (%.0f%% of income)", expenses, pct));
            categories.forEach((k, v) -> r.append(String.format("\n\u2022 %s: $%.2f", k, v)));
            if (!receipts.isEmpty()) {
                r.append(String.format("\n\n\uD83E\uDDFE Actual spending (receipts this month): $%.2f", actualTotal));
                r.append("\nAsk 'show actual' to see the full breakdown.");
            }
            return r.toString();
        }

        // ── Compare budget vs actual ──
        if (matches(msg, "compare", "budget vs actual", "am i on track", "how am i tracking", "vs actual", "vs budget")) {
            if (budget == null) return noBudget();
            if (receipts.isEmpty())
                return String.format("\uD83D\uDCCA Set budget: $%.2f/month\n\uD83E\uDDFE Actual (receipts): $0.00\n\nNo receipts scanned yet this month. Scan receipts to compare!", expenses);
            double diff = expenses - actualTotal;
            StringBuilder r = new StringBuilder("\uD83C\uDD9A Budget vs Actual (this month):\n");
            r.append(String.format("\u2022 Set budget:   $%.2f\n", expenses));
            r.append(String.format("\u2022 Actual spent: $%.2f\n", actualTotal));
            if (diff >= 0)
                r.append(String.format("\u2705 You're $%.2f under budget \u2014 on track!", diff));
            else
                r.append(String.format("\u26A0\uFE0F You're $%.2f over your set budget.", Math.abs(diff)));
            r.append("\n\nVisit 'Spending Visualization' for full charts!");
            return r.toString();
        }

        // ── Recent receipts ──
        if (matches(msg, "recent receipt", "latest expense", "last receipt", "what did i buy", "recent purchase", "recent spending", "last purchase")) {
            if (receipts.isEmpty())
                return "No receipts found this month. Start scanning receipts to track your spending!";
            List<ScannedReceipt> sorted = new ArrayList<>(receipts);
            sorted.sort((a, b) -> b.getDateStr().compareTo(a.getDateStr()));
            StringBuilder r = new StringBuilder(String.format("\uD83E\uDDFE Recent expenses this month (%d total):\n", receipts.size()));
            int show = Math.min(5, sorted.size());
            for (int i = 0; i < show; i++) {
                ScannedReceipt rec = sorted.get(i);
                r.append(String.format("\u2022 %s \u2014 $%.2f (%s)",
                    rec.getMerchantName() != null ? rec.getMerchantName() : "Unknown",
                    rec.getAmount(),
                    rec.getCategory() != null ? rec.getCategory() : "Other"));
                if (rec.getDateStr() != null) r.append(" on ").append(rec.getDateStr());
                r.append("\n");
            }
            if (sorted.size() > 5) r.append("...and ").append(sorted.size() - 5).append(" more.");
            return r.toString().trim();
        }

        // ── Full summary / all data ──
        if (matches(msg, "summary", "overview", "show all", "all data", "everything", "full report", "my data", "give me everything", "full summary")) {
            if (user == null) return "Please log in to view your data.";
            StringBuilder r = new StringBuilder("\uD83D\uDCCB Your Complete Financial Summary:\n");

            // Profile
            r.append("\n\uD83D\uDC64 Profile:\n");
            r.append("\u2022 Username: ").append(user.getUsername()).append("\n");
            if (user.getName()  != null && !user.getName().isBlank())  r.append("\u2022 Name: ").append(user.getName()).append("\n");
            if (user.getEmail() != null && !user.getEmail().isBlank()) r.append("\u2022 Email: ").append(user.getEmail()).append("\n");

            // Set Budget
            if (budget != null) {
                r.append(String.format("\n\uD83D\uDCB0 Income: $%.2f/month\n", income));
                r.append(String.format("\uD83D\uDCCA Set Budget Expenses: $%.2f\n", expenses));
                categories.forEach((k, v) -> r.append(String.format("  \u2022 %s: $%.2f\n", k, v)));
                r.append(String.format("\uD83D\uDC37 Savings: $%.2f (%.0f%%)\n", savings, savingsRate));
                r.append("\u2764\uFE0F Health: ").append(status.substring(0, 1).toUpperCase()).append(status.substring(1)).append("\n");
            } else {
                r.append("\n\u26A0\uFE0F No budget set yet.\n");
            }

            // Actual
            if (!receipts.isEmpty()) {
                r.append(String.format("\n\uD83E\uDDFE Actual Spent (this month): $%.2f\n", actualTotal));
                actualCategories.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> r.append(String.format("  \u2022 %s: $%.2f\n", e.getKey(), e.getValue())));
                if (budget != null) {
                    double diff = expenses - actualTotal;
                    r.append(diff >= 0
                        ? String.format("\u2705 $%.2f under budget\n", diff)
                        : String.format("\u26A0\uFE0F $%.2f over budget\n", Math.abs(diff)));
                }
            } else {
                r.append("\n\uD83E\uDDFE No receipts scanned this month.\n");
            }

            return r.toString().trim();
        }

        // ── Savings ──
        if (matches(msg, "saving", "savings", "how much left", "remaining", "how much do i save", "saved", "net")) {
            if (budget == null) return noBudget();
            if (savings >= 0) {
                return String.format("\uD83D\uDC37 You're saving $%.2f/month (%.0f%% savings rate).\n%s",
                        savings, savingsRate,
                        savingsRate >= 20
                                ? "Excellent! You're above the recommended 20%. Consider investing the surplus."
                                : "Try to reach the recommended 20% rate. Small cuts in discretionary spending add up!");
            } else {
                return String.format("\u26A0\uFE0F You're overspending by $%.2f/month.\nYour expenses exceed income. Review your largest expense categories to find savings.", Math.abs(savings));
            }
        }

        // ── Savings rate ──
        if (matches(msg, "savings rate", "saving rate", "how much percent", "what percent")) {
            if (budget == null) return noBudget();
            return String.format("\uD83D\uDCC8 Your savings rate is %.0f%%.\n%s", savingsRate,
                    savingsRate >= 20 ? "Great job! You're above the recommended 20%." :
                    savingsRate >= 10 ? "Progress! Try to reach 20% by cutting non-essentials." :
                    "Below recommended levels. Focus on reducing your largest expenses first.");
        }

        // ── Budget health ──
        if (matches(msg, "health", "how am i doing", "budget status", "financial health", "am i okay", "am i overspend", "status", "doing good")) {
            if (budget == null) return noBudget();

            // Use actual receipts when available; fall back to set budget
            boolean hHasActual    = !receipts.isEmpty();
            double  hExpenses     = hHasActual ? actualTotal : expenses;
            double  hSavings      = income - hExpenses;
            double  hSavingsRate  = income > 0 ? (hSavings / income) * 100 : 0;
            String  hStatus       = hSavings < 0 ? "overspending"
                                  : (hSavings < income * 0.2 ? "low" : "healthy");
            String  hExpLabel     = hHasActual ? "actual spending" : "set budget";
            String  hHighCat      = hHasActual ? highestActualCat : highestCat;
            double  hHighAmt      = hHighCat != null
                                  ? (hHasActual ? actualCategories.getOrDefault(hHighCat, 0.0)
                                                : categories.getOrDefault(hHighCat, 0.0))
                                  : 0;

            switch (hStatus) {
                case "healthy":
                    return String.format(
                        "\u2764\uFE0F **Budget Health: Healthy!**\n"
                        + "You\u2019re saving **%.0f%%** of income (**$%.2f/month**).\n"
                        + "_(Based on %s)_\n\n"
                        + "Your finances are in great shape. Keep it up!",
                        hSavingsRate, hSavings, hExpLabel);
                case "low":
                    return String.format(
                        "\u26A0\uFE0F **Budget Health: Low Savings**\n"
                        + "You\u2019re only saving **%.0f%%** ($%.2f/month). Recommended minimum is 20%%.\n"
                        + "_(Based on %s)_\n%s",
                        hSavingsRate, hSavings, hExpLabel,
                        hHighCat != null
                            ? "\nBiggest expense: **" + hHighCat + "** ($" + String.format("%.2f", hHighAmt) + "). Look for savings there."
                            : "\nTry to increase income or reduce expenses.");
                case "overspending":
                    return String.format(
                        "\uD83D\uDEA8 **Budget Health: Overspending!**\n"
                        + "You\u2019re over budget by **$%.2f/month**. Expenses ($%.2f) exceed income ($%.2f).\n"
                        + "_(Based on %s)_%s",
                        Math.abs(hSavings), hExpenses, income, hExpLabel,
                        hHighCat != null
                            ? "\nLargest expense: **" + hHighCat + "** ($" + String.format("%.2f", hHighAmt) + "). Reducing it would help most."
                            : "");
                default:
                    return noBudget();
            }
        }

        // ── Budget categories ──
        if (matches(msg, "categor", "breakdown", "where does my money go", "spending on", "set budget categories", "budget breakdown")) {
            if (budget == null) return noBudget();
            if (categories.isEmpty()) return "No expense categories found. Visit 'Set Budget' to add them.";
            StringBuilder r = new StringBuilder("\uD83D\uDCC2 Set budget breakdown (highest first):");
            categories.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .forEach(e -> r.append(String.format("\n\u2022 %s: $%.2f (%.0f%%)",
                            e.getKey(), e.getValue(), expTotal > 0 ? e.getValue() / expTotal * 100 : 0)));
            return r.toString();
        }

        // ── Account number ──
        if (matches(msg, "account number", "my account", "acc number", "account #", "account no")) {
            if (user == null) return "Please log in to view account info.";
            if (user.getAccountNumber() == null || user.getAccountNumber().isBlank())
                return "No account number saved yet. Go to your Profile to view it.";
            String num = user.getAccountNumber();
            String masked = "****" + num.substring(Math.max(0, num.length() - 4));
            return "\uD83C\uDFE6 Account number: " + masked + "\n(Last 4 digits shown for security. Full number is in your Profile.)";
        }

        // ── Routing number ──
        if (matches(msg, "routing", "routing number", "aba", "transit number")) {
            if (user == null) return "Please log in to view account info.";
            if (user.getRoutingNumber() == null || user.getRoutingNumber().isBlank())
                return "No routing number saved yet. Go to your Profile to view it.";
            String num = user.getRoutingNumber();
            String masked = "****" + num.substring(Math.max(0, num.length() - 4));
            return "\uD83C\uDFE6 Routing number: " + masked + "\n(Last 4 digits shown for security. Full number is in your Profile.)";
        }

        // ── Predictive Cash Flow — full 6-month forecast ──
        if (matches(msg, "cash flow", "predictive cash flow", "forecast", "next month", "projection",
                    "predict", "future money", "6 month", "six month", "monthly forecast")) {
            if (budget == null) return noBudget();

            // Use actual receipts for expenses when available (same as the page does)
            double cfExpenses   = !receipts.isEmpty() ? actualTotal : expenses;
            double cfNet        = income - cfExpenses;
            double cfRate       = income > 0 ? (cfNet / income) * 100 : 0;
            String expSource    = !receipts.isEmpty() ? "actual receipts" : "set budget";
            String trajectory   = cfNet > 0 ? "positive" : (cfNet < 0 ? "negative" : "neutral");

            // Build 6-month cumulative forecast
            String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            DateTimeFormatter mFmt = DateTimeFormatter.ofPattern("MMM yyyy");
            String[] monthLabels = new String[6];
            double[] cumulative  = new double[6];
            double running = 0;
            for (int i = 0; i < 6; i++) {
                running += cfNet;
                monthLabels[i] = LocalDate.now().plusMonths(i).format(mFmt);
                cumulative[i]  = running;
            }
            double sixMonthTotal = cumulative[5];

            StringBuilder r = new StringBuilder();

            // ── Header ──
            r.append("\uD83D\uDCC8 **Predictive Cash Flow \u2014 ").append(currentMonth).append("**\n");
            r.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");

            // ── Monthly snapshot ──
            r.append("\uD83D\uDCB0 **MONTHLY SNAPSHOT**\n");
            r.append(String.format("\u2022 Monthly Income:    **$%.2f**\n", income));
            r.append(String.format("\u2022 Monthly Expenses:  $%.2f  (%s)\n", cfExpenses, expSource));
            r.append(String.format("\u2022 Monthly Net:       **%s$%.2f**\n", cfNet >= 0 ? "+" : "-", Math.abs(cfNet)));
            r.append(String.format("\u2022 Savings Rate:      **%.0f%%**\n", cfRate));
            r.append(String.format("\u2022 6-Month Total:     **%s$%.2f**\n", sixMonthTotal >= 0 ? "+" : "-", Math.abs(sixMonthTotal)));
            r.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

            // ── Trajectory ──
            switch (trajectory) {
                case "positive":
                    r.append(String.format("\uD83D\uDCC8 **Trajectory: POSITIVE**\n"
                        + "At your current rate you\u2019ll accumulate **$%.2f** over 6 months.\n"
                        + "Your %.0f%% savings rate puts you on a strong financial path!\n\n",
                        sixMonthTotal, cfRate));
                    break;
                case "negative":
                    r.append(String.format("\uD83D\uDCC9 **Trajectory: NEGATIVE**\n"
                        + "At your current rate you\u2019ll accumulate **$%.2f in debt** over 6 months.\n"
                        + "Reduce expenses or increase income to reverse this trend.\n\n",
                        Math.abs(sixMonthTotal)));
                    break;
                default:
                    r.append("\u2696\uFE0F **Trajectory: BREAK-EVEN**\n"
                        + "Income exactly covers expenses. Trim even $50/month \u2014 it compounds over time.\n\n");
            }

            // ── Month-by-month breakdown ──
            r.append("\uD83D\uDCC5 **6-MONTH FORECAST**\n");
            for (int i = 0; i < 6; i++) {
                String sign = cumulative[i] >= 0 ? "+" : "-";
                r.append(String.format("\u2022 Month %d  %-10s  %s$%-10.2f  Income: $%.0f \u00B7 Exp: $%.0f\n",
                        i + 1, monthLabels[i], sign, Math.abs(cumulative[i]), income, cfExpenses));
            }

            r.append("\n\u2139\uFE0F Forecast assumes consistent monthly income and expenses.\n");
            r.append("\uD83D\uDC49 Visit **Predictive Cash Flow** in the sidebar for full charts!");
            return r.toString();
        }

        // ── Goals ──
        if (matches(msg, "goal", "target", "emergency fund", "dream", "set goal", "financial goal")) {
            if (budget == null) return "Set up your budget first, then I can give personalized goal recommendations! Visit 'Set Budget' in the sidebar.";
            return String.format("\uD83C\uDFAF Financial goals for you:\n\u2022 Emergency fund: $%.0f\u2013$%.0f (3\u20136 months of expenses)\n\u2022 Retirement: Aim to save at least 15%% of income\n\u2022 Short-term: Set aside a fixed amount each month\n\nVisit 'Goal Tracking' in the sidebar to set and monitor progress!",
                    expenses * 3, expenses * 6);
        }

        // ── Tips / advice ──
        if (matches(msg, "tip", "advice", "suggest", "help me save", "how to save", "improve", "recommend", "financial advice", "what should i do", "guide me")) {
            if (budget == null) {
                return "\uD83D\uDCA1 General financial tips:\n\n1. 50/30/20 rule: 50% needs, 30% wants, 20% savings\n2. Build a 3\u20136 month emergency fund first\n3. Automate savings on payday\n4. Track every expense for 30 days\n5. Cancel unused subscriptions\n6. Invest early \u2014 compound interest is powerful!\n\nSet up your budget for personalized advice!";
            }

            // Use actual receipts when available; fall back to set budget
            boolean hasActual       = !receipts.isEmpty();
            double  tipExpenses     = hasActual ? actualTotal : expenses;
            double  tipSavings      = income - tipExpenses;
            double  tipSavingsRate  = income > 0 ? (tipSavings / income) * 100 : 0;
            String  tipStatus       = tipSavings < 0 ? "overspending"
                                    : (tipSavings < income * 0.2 ? "low" : "healthy");
            String  expLabel        = hasActual ? "actual spending" : "set budget";

            // Highest actual category (for personalized advice when using receipts)
            String tipHighCat = hasActual ? highestActualCat : highestCat;
            double tipHighAmt = 0;
            if (tipHighCat != null) {
                tipHighAmt = hasActual
                    ? actualCategories.getOrDefault(tipHighCat, 0.0)
                    : categories.getOrDefault(tipHighCat, 0.0);
            }

            StringBuilder r = new StringBuilder("\uD83D\uDCA1 Personalized tips for " + name + ":\n");
            r.append(String.format("_(Based on %s — $%.2f/month)_\n\n", expLabel, tipExpenses));

            if (tipStatus.equals("overspending")) {
                r.append(String.format("\uD83D\uDEA8 You're overspending by **$%.2f/month**.\n", Math.abs(tipSavings)));
                if (tipHighCat != null) r.append(String.format("\u2022 Cut **%s** ($%.2f) first\n", tipHighCat, tipHighAmt));
                r.append("\u2022 Cancel unused subscriptions\n\u2022 Cook at home to reduce food costs\n\u2022 Set a daily spending limit\n\u2022 Use the 48-hour rule before big purchases");
            } else if (tipStatus.equals("low")) {
                r.append(String.format("\uD83D\uDCC8 Savings rate: **%.0f%%** \u2014 target is 20%%\n", tipSavingsRate));
                if (tipHighCat != null) r.append(String.format("\u2022 Reduce **%s** ($%.2f) to boost savings\n", tipHighCat, tipHighAmt));
                r.append("\u2022 Automate savings transfers on payday\n\u2022 Try meal prepping to cut grocery bills\n\u2022 Negotiate bills (insurance, phone, internet)\n\u2022 Consider a side income stream");
            } else {
                r.append(String.format("\u2705 Great! Saving **%.0f%%** ($%.2f/month).\n", tipSavingsRate, tipSavings));
                r.append("\u2022 Invest in low-cost index funds\n\u2022 Max out 401k/IRA contributions\n\u2022 Build emergency fund to 6 months\n\u2022 Consider dividend stocks or real estate\n\u2022 Stay consistent \u2014 time in market beats timing!");
            }
            return r.toString();
        }

        // ── Thank you ──
        if (matches(msg, "thank", "thanks", "thank you", "great", "awesome", "perfect", "nice")) {
            return "You're welcome, " + name + "! \uD83D\uDE0A I'm always here to help with your finances. Keep working toward your goals \u2014 you've got this! \uD83D\uDCAA";
        }

        // ── Bye ──
        if (matches(msg, "bye", "goodbye", "see you", "close", "exit")) {
            return "Goodbye, " + name + "! \uD83D\uDC4B Stay financially savvy and feel free to come back anytime!";
        }

        // ── Default ──
        return "I'm not sure I understood that. \uD83E\uDD14\n\nTry asking:\n\u2022 'What's my email?'\n\u2022 'What's my full name?'\n\u2022 'What are my actual expenses?'\n\u2022 'Show actual categories'\n\u2022 'Budget vs actual'\n\u2022 'Show all my data'\n\nOr type 'help' for all commands!";
    }

    private boolean matches(String msg, String... keywords) {
        for (String kw : keywords) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    private String noBudget() {
        return "You haven't set up a budget yet. Visit 'Set Budget' in the sidebar to get started \u2014 then I can give you personalized financial insights! \uD83D\uDCCA";
    }

    private String getDisplayName(User user) {
        if (user == null) return "there";
        return (user.getName() != null && !user.getName().isBlank()) ? user.getName() : user.getUsername();
    }
}
