package com.finvision.controller;

import com.finvision.model.Budget;
import com.finvision.model.ScannedReceipt;
import com.finvision.repository.BudgetRepository;
import com.finvision.repository.ScannedReceiptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
public class ReceiptController {

    @Autowired
    private ScannedReceiptRepository receiptRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    /** Show the Receipt Scanner page */
    @GetMapping("/receipt-scan")
    public String receiptScan(Principal principal, Model model) {
        if (principal == null) return "redirect:/login";
        String username = principal.getName();

        List<ScannedReceipt> receipts = receiptRepository.findByUsernameOrderByDateStrDesc(username);

        // Current-month total
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        double monthTotal = receipts.stream()
            .filter(r -> yearMonth.equals(r.getYearMonth()))
            .mapToDouble(ScannedReceipt::getAmount)
            .sum();

        model.addAttribute("receipts", receipts);
        model.addAttribute("monthTotal", String.format("%.2f", monthTotal));
        model.addAttribute("receiptCount", receipts.size());
        model.addAttribute("todayStr", LocalDate.now().toString());

        // ── Build Budget vs Actual comparison ──────────────────────────────────
        Budget budget = budgetRepository.findTopByUsernameOrderByIdDesc(username).orElse(null);

        // Sum actual receipt amounts by MERCHANT NAME (case-insensitive, this month only)
        Map<String, Double> actualByMerchant = new LinkedHashMap<>();
        Map<String, String> merchantDisplay  = new LinkedHashMap<>(); // lowercase key → display name
        for (ScannedReceipt r : receipts) {
            if (yearMonth.equals(r.getYearMonth())) {
                String merchant = (r.getMerchantName() != null && !r.getMerchantName().trim().isEmpty())
                        ? r.getMerchantName().trim() : "Unknown";
                String key = merchant.toLowerCase();
                actualByMerchant.merge(key, r.getAmount(), Double::sum);
                merchantDisplay.putIfAbsent(key, merchant);
            }
        }

        List<Map<String, Object>> compRows = new ArrayList<>();
        Set<String> budgetCatNamesLower = new HashSet<>();

        if (budget != null) {
            // Fixed budget categories — match against merchant name (case-insensitive)
            String[][] fixed = {
                {"Rent",          String.valueOf(budget.getRent())},
                {"Utilities",     String.valueOf(budget.getUtilities())},
                {"Insurance",     String.valueOf(budget.getInsurance())},
                {"Groceries",     String.valueOf(budget.getGroceries())},
                {"Subscriptions", String.valueOf(budget.getSubscriptions())}
            };
            for (String[] fc : fixed) {
                budgetCatNamesLower.add(fc[0].toLowerCase());
                double set    = Double.parseDouble(fc[1]);
                double actual = actualByMerchant.getOrDefault(fc[0].toLowerCase(), 0.0);
                compRows.add(buildRow(fc[0], set, actual));
            }

            // Variable budget categories
            List<String> varTitles  = budget.getVariableTitle();
            List<Double> varAmounts = budget.getVariableAmount();
            if (varTitles != null && varAmounts != null) {
                for (int i = 0; i < Math.min(varTitles.size(), varAmounts.size()); i++) {
                    String title = varTitles.get(i);
                    Double amt   = varAmounts.get(i);
                    if (title != null && !title.trim().isEmpty()) {
                        budgetCatNamesLower.add(title.trim().toLowerCase());
                        double set    = (amt != null) ? amt : 0.0;
                        double actual = actualByMerchant.getOrDefault(title.trim().toLowerCase(), 0.0);
                        compRows.add(buildRow(title, set, actual));
                    }
                }
            }
        }

        // Merchant names that don't match any budget category → individual rows
        for (Map.Entry<String, Double> entry : actualByMerchant.entrySet()) {
            if (!budgetCatNamesLower.contains(entry.getKey())) {
                String displayName = merchantDisplay.getOrDefault(entry.getKey(), entry.getKey());
                compRows.add(buildRow(displayName, 0.0, entry.getValue()));
            }
        }

        // Totals
        double totalSet    = compRows.stream().mapToDouble(r -> (Double) r.get("setAmount")).sum();
        double totalActual = compRows.stream().mapToDouble(r -> (Double) r.get("actualAmount")).sum();

        model.addAttribute("compRows",       compRows);
        model.addAttribute("compTotalSet",    String.format("%.2f", totalSet));
        model.addAttribute("compTotalActual", String.format("%.2f", totalActual));
        model.addAttribute("hasBudget",       budget != null);

        return "ReceiptScan";
    }

    /** Helper: build one comparison row map */
    private Map<String, Object> buildRow(String category, double set, double actual) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("category",     category);
        row.put("setAmount",    set);
        row.put("actualAmount", actual);

        double diff = set - actual;       // positive = under budget, negative = over budget
        row.put("diff", diff);

        String feedback, status;
        if (set == 0 && actual > 0) {
            feedback = "Unbudgeted";           status = "warn";
        } else if (actual == 0 && set > 0) {
            feedback = "Not spent yet";        status = "none";
        } else if (actual == 0) {
            feedback = "No data";              status = "none";
        } else if (actual > set) {
            feedback = "You spend more on this"; status = "over";
        } else if (actual < set) {
            feedback = "You spend less on this"; status = "good";
        } else {
            feedback = "Exactly on budget";    status = "good";
        }
        row.put("feedback", feedback);
        row.put("status",   status);
        return row;
    }

    /** Save a new scanned receipt */
    @PostMapping("/receipt-scan/add")
    public String addReceipt(
            @RequestParam(required = false) String merchantName,
            @RequestParam double amount,
            @RequestParam String category,
            @RequestParam String dateStr,
            @RequestParam(required = false) String notes,
            @RequestParam(value = "receiptImage", required = false) MultipartFile receiptImage,
            Principal principal) {

        if (principal == null) return "redirect:/login";

        ScannedReceipt receipt = new ScannedReceipt();
        receipt.setUsername(principal.getName());
        receipt.setMerchantName(merchantName != null ? merchantName.trim() : "Unknown");
        receipt.setAmount(amount);
        receipt.setCategory(category);
        receipt.setDateStr(dateStr);
        receipt.setYearMonth(dateStr.length() >= 7 ? dateStr.substring(0, 7) : "");
        receipt.setNotes(notes != null ? notes.trim() : "");

        if (receiptImage != null && !receiptImage.isEmpty()) {
            try {
                String ct = receiptImage.getContentType();
                if (ct == null) ct = "image/jpeg";
                String b64 = Base64.getEncoder().encodeToString(receiptImage.getBytes());
                receipt.setImageData("data:" + ct + ";base64," + b64);
            } catch (Exception ignored) {}
        }

        receiptRepository.save(receipt);
        return "redirect:/receipt-scan";
    }

    /** Delete a receipt by ID */
    @PostMapping("/receipt-scan/delete/{id}")
    public String deleteReceipt(@PathVariable String id, Principal principal) {
        if (principal == null) return "redirect:/login";
        receiptRepository.findById(id).ifPresent(r -> {
            if (r.getUsername().equals(principal.getName())) {
                receiptRepository.delete(r);
            }
        });
        return "redirect:/receipt-scan";
    }
}
