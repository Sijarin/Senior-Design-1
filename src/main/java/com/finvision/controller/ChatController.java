package com.finvision.controller;

import com.finvision.model.Budget;
import com.finvision.model.User;
import com.finvision.repository.BudgetRepository;
import com.finvision.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.*;

@Controller
public class ChatController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @PostMapping("/chat")
    @ResponseBody
    public Map<String, String> chat(@RequestBody Map<String, String> body, Principal principal) {
        String msg = body.getOrDefault("message", "").toLowerCase().trim();
        User user = null;
        Budget budget = null;
        if (principal != null) {
            user   = userRepository.findByUsername(principal.getName()).orElse(null);
            budget = budgetRepository.findTopByUsernameOrderByIdDesc(principal.getName()).orElse(null);
        }
        Map<String, String> result = new HashMap<>();
        result.put("response", generateResponse(msg, user, budget));
        return result;
    }

    private String generateResponse(String msg, User user, Budget budget) {
        String name = getDisplayName(user);

        // Compute budget stats
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
            savings    = income - expenses;
            savingsRate = income > 0 ? (savings / income) * 100 : 0;
            status     = savings < 0 ? "overspending" : (savings < income * 0.2 ? "low" : "healthy");
        }

        final double expTotal = expenses;
        final double inc      = income;
        final double sav      = savings;
        final double rate     = savingsRate;
        String highestCat = categories.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);

        // ── Greetings ──
        if (matches(msg, "hello", "hi ", "hey ", "good morning", "good evening", "howdy", "what's up", "sup")) {
            return "Hello " + name + "! \uD83D\uDC4B I'm FinVision AI, your personal finance assistant.\n\nI can help with:\n\u2022 Budget & spending insights\n\u2022 Savings & financial health\n\u2022 Account information\n\u2022 Personalized financial tips\n\nType 'help' for all commands, or just ask anything!";
        }

        // ── Help ──
        if (matches(msg, "help", "what can you do", "commands", "capabilities", "menu")) {
            return "Here's what I can answer:\n\n\uD83D\uDCB0 Income \u2014 'What's my income?'\n\uD83D\uDCCA Expenses \u2014 'Show my expenses'\n\uD83D\uDC37 Savings \u2014 'How much am I saving?'\n\u2764\uFE0F Health \u2014 'How's my budget health?'\n\uD83D\uDCC2 Categories \u2014 'Show spending breakdown'\n\uD83C\uDFE6 Banking \u2014 'What's my account number?'\n\uD83D\uDC64 Profile \u2014 'Who am I?'\n\uD83D\uDCA1 Tips \u2014 'Give me financial advice'\n\uD83D\uDCC8 Forecast \u2014 'Show my cash flow'\n\uD83C\uDFAF Goals \u2014 'Help me set financial goals'\n\nYou can also use the \uD83C\uDFA4 microphone for voice commands!";
        }

        // ── Income ──
        if (matches(msg, "income", "earn", "salary", "how much do i make", "monthly income", "what i make", "my income")) {
            if (budget == null) return noBudget();
            StringBuilder r = new StringBuilder(String.format("\uD83D\uDCB0 Your total monthly income is $%.2f.", income));
            if (budget.getMonthlyIncome() > 0) r.append(String.format("\n\u2022 Primary income: $%.2f", budget.getMonthlyIncome()));
            if (budget.getOtherIncome()   > 0) r.append(String.format("\n\u2022 Other income: $%.2f",   budget.getOtherIncome()));
            return r.toString();
        }

        // ── Expenses ──
        if (matches(msg, "expense", "spend", "how much do i spend", "total expense", "my expenses", "costs", "what i spend")) {
            if (budget == null) return noBudget();
            double pct = income > 0 ? (expenses / income) * 100 : 0;
            StringBuilder r = new StringBuilder(String.format("\uD83D\uDCCA Total monthly expenses: $%.2f (%.0f%% of income).", expenses, pct));
            categories.forEach((k, v) -> r.append(String.format("\n\u2022 %s: $%.2f", k, v)));
            return r.toString();
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
        if (matches(msg, "savings rate", "saving rate", "how much percent", "what percent", "rate")) {
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

        // ── Categories ──
        if (matches(msg, "categor", "breakdown", "where does my money go", "what am i spending on", "spending on")) {
            if (budget == null) return noBudget();
            if (categories.isEmpty()) return "No expense categories found. Visit 'Set Budget' to add them.";
            StringBuilder r = new StringBuilder("\uD83D\uDCC2 Spending breakdown (highest first):");
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
                return "No account number saved yet. Go to your Profile to add it.";
            String num = user.getAccountNumber();
            String masked = "****" + num.substring(Math.max(0, num.length() - 4));
            return "\uD83C\uDFE6 Account number: " + masked + "\n(Last 4 digits shown for security. Full number is in your Profile.)";
        }

        // ── Routing number ──
        if (matches(msg, "routing", "routing number", "aba", "transit number")) {
            if (user == null) return "Please log in to view account info.";
            if (user.getRoutingNumber() == null || user.getRoutingNumber().isBlank())
                return "No routing number saved yet. Go to your Profile to add it.";
            String num = user.getRoutingNumber();
            String masked = "****" + num.substring(Math.max(0, num.length() - 4));
            return "\uD83C\uDFE6 Routing number: " + masked + "\n(Last 4 digits shown for security. Full number is in your Profile.)";
        }

        // ── Profile ──
        if (matches(msg, "who am i", "my name", "my profile", "my info", "about me", "profile info")) {
            if (user == null) return "You're not logged in.";
            StringBuilder r = new StringBuilder("\uD83D\uDC64 Your profile:\n");
            r.append("\u2022 Username: ").append(user.getUsername());
            if (user.getName()  != null && !user.getName().isBlank())  r.append("\n\u2022 Name: ").append(user.getName());
            if (user.getEmail() != null && !user.getEmail().isBlank()) r.append("\n\u2022 Email: ").append(user.getEmail());
            return r.toString();
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
        return "I'm not sure I understood that. \uD83E\uDD14\n\nTry asking:\n\u2022 'What's my income?'\n\u2022 'Show my expenses'\n\u2022 'How's my budget health?'\n\u2022 'Give me financial advice'\n\u2022 'What's my account number?'\n\nOr type 'help' for all commands!";
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
