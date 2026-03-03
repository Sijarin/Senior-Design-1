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
            return "Here's what I can answer:\n\n\uD83D\uDCB0 Income \u2014 'What's my income?'\n\uD83D\uDCCA Set Budget \u2014 'Show my set expenses'\n\uD83E\uDDFE Actual Expenses \u2014 'What did I actually spend?'\n\uD83D\uDCC2 Categories \u2014 'Show actual categories'\n\uD83C\uDD9A Compare \u2014 'Budget vs actual'\n\uD83D\uDC37 Savings \u2014 'How much am I saving?'\n\u2764\uFE0F Health \u2014 'How's my budget health?'\n\uD83D\uDC64 Profile \u2014 'My name / email / username'\n\uD83C\uDFE6 Banking \u2014 'My account number'\n\uD83D\uDCA1 Tips \u2014 'Give me financial advice'\n\uD83D\uDCC8 Forecast \u2014 'Show my cash flow'\n\uD83C\uDFAF Goals \u2014 'Help me set financial goals'\n\uD83D\uDCCB Summary \u2014 'Show all my data'\n\n\uD83C\uDFA4 You can also use the microphone for voice commands!";
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

        // ── Set Budget Expenses ──
        if (matches(msg, "set budget expense", "set expense", "planned expense", "my set expense", "what is set budget", "budgeted expense")) {
            if (budget == null) return noBudget();
            double pct = income > 0 ? (expenses / income) * 100 : 0;
            StringBuilder r = new StringBuilder(String.format("\uD83D\uDCCA Set budget — $%.2f/month (%.0f%% of income)\n", expenses, pct));
            categories.forEach((k, v) -> r.append(String.format("\u2022 %s: $%.2f\n", k, v)));
            return r.toString().trim();
        }

        // ── Expenses (general) ──
        if (matches(msg, "expense", "spend", "how much do i spend", "total expense", "my expenses", "costs", "what i spend")) {
            if (budget == null) return noBudget();
            double pct = income > 0 ? (expenses / income) * 100 : 0;
            StringBuilder r = new StringBuilder(String.format("\uD83D\uDCCA Set budget — Total: $%.2f/month (%.0f%% of income)", expenses, pct));
            categories.forEach((k, v) -> r.append(String.format("\n\u2022 %s: $%.2f", k, v)));
            if (!receipts.isEmpty()) {
                r.append(String.format("\n\n\uD83E\uDDFE Actual spending (receipts this month): $%.2f", actualTotal));
                r.append("\nAsk 'show actual categories' to see the breakdown.");
            }
            return r.toString();
        }

        // ── Actual expenses ──
        if (matches(msg, "actual expense", "actual spend", "actual cost", "what did i actually spend", "real expense",
                    "receipt total", "my receipts", "scanned receipt", "actual amount", "how much did i spend")) {
            if (receipts.isEmpty())
                return "\uD83E\uDDFE No receipts scanned this month yet. Use the 'Scan Receipt' feature to log your actual expenses.";
            StringBuilder r = new StringBuilder(String.format("\uD83E\uDDFE Actual spending this month: $%.2f\n", actualTotal));
            if (budget != null) {
                double diff = expenses - actualTotal;
                r.append(String.format("\u2022 Set budget: $%.2f\n", expenses));
                if (diff >= 0)
                    r.append(String.format("\u2705 You're $%.2f under budget \u2014 great job!", diff));
                else
                    r.append(String.format("\u26A0\uFE0F You're $%.2f over your set budget.", Math.abs(diff)));
            }
            return r.toString();
        }

        // ── Actual expense categories ──
        if (matches(msg, "actual categor", "receipt categor", "what did i spend on", "actual breakdown", "show actual", "actual by category")) {
            if (receipts.isEmpty())
                return "No receipts found this month. Scan receipts to track your actual spending by category.";
            StringBuilder r = new StringBuilder(String.format("\uD83E\uDDFE Actual spending by category (this month \u2014 $%.2f total):\n", actualTotal));
            actualCategories.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> r.append(String.format("\u2022 %s: $%.2f (%.0f%%)\n",
                    e.getKey(), e.getValue(), actualTotal > 0 ? e.getValue() / actualTotal * 100 : 0)));
            if (highestActualCat != null)
                r.append("\nHighest: ").append(highestActualCat)
                 .append(" ($").append(String.format("%.2f", actualCategories.get(highestActualCat))).append(")");
            return r.toString().trim();
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
            switch (status) {
                case "healthy":
                    return String.format("\u2764\uFE0F Budget health: Healthy!\nYou're saving %.0f%% of income ($%.2f/month). Your finances are in great shape. Keep it up!", savingsRate, savings);
                case "low":
                    return String.format("\u26A0\uFE0F Budget health: Low Balance.\nYou're only saving %.0f%% ($%.2f/month). Recommended minimum is 20%%.\n%s",
                            savingsRate, savings,
                            highestCat != null ? "Biggest expense: " + highestCat + " ($" + String.format("%.2f", categories.get(highestCat)) + "). Look for savings there." : "Try to increase income or reduce expenses.");
                case "overspending":
                    return String.format("\uD83D\uDEA8 Budget health: Overspending!\nYou're over budget by $%.2f/month. Expenses ($%.2f) exceed income ($%.2f).%s",
                            Math.abs(savings), expenses, income,
                            highestCat != null ? "\nLargest expense: " + highestCat + " ($" + String.format("%.2f", categories.get(highestCat)) + "). Reducing it would help most." : "");
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

        // ── Cash flow ──
        if (matches(msg, "cash flow", "forecast", "next month", "projection", "predict", "future money")) {
            if (budget == null) return noBudget();
            String sign = savings >= 0 ? "+" : "-";
            return String.format("\uD83D\uDCC8 Cash flow forecast:\n\u2022 Monthly net: %s$%.2f\n\u2022 3-month: %s$%.2f\n\u2022 6-month: %s$%.2f\n\nVisit 'Predictive Cash Flow' in the sidebar for full charts!",
                    sign, Math.abs(savings), sign, Math.abs(savings * 3), sign, Math.abs(savings * 6));
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
            StringBuilder r = new StringBuilder("\uD83D\uDCA1 Personalized tips for " + name + ":\n\n");
            if (status.equals("overspending")) {
                r.append(String.format("\uD83D\uDEA8 You're overspending by $%.2f/month.\n", Math.abs(savings)));
                if (highestCat != null) r.append(String.format("\u2022 Cut %s expenses ($%.2f) first\n", highestCat, categories.get(highestCat)));
                r.append("\u2022 Cancel unused subscriptions\n\u2022 Cook at home to reduce food costs\n\u2022 Set a daily spending limit\n\u2022 Use the 48-hour rule before big purchases");
            } else if (status.equals("low")) {
                r.append(String.format("\uD83D\uDCC8 Savings rate: %.0f%% \u2014 target is 20%%\n", savingsRate));
                if (highestCat != null) r.append(String.format("\u2022 Reduce %s ($%.2f) to boost savings\n", highestCat, categories.get(highestCat)));
                r.append("\u2022 Automate savings transfers on payday\n\u2022 Try meal prepping to cut grocery bills\n\u2022 Negotiate bills (insurance, phone, internet)\n\u2022 Consider a side income stream");
            } else {
                r.append(String.format("\u2705 Great! Saving %.0f%% ($%.2f/month).\n", savingsRate, savings));
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
