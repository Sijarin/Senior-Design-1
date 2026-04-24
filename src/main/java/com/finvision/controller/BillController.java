package com.finvision.controller;

import com.finvision.model.Bill;
import com.finvision.repository.BillRepository;
import com.finvision.service.BillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles HTTP endpoints for bill creation, editing, payment tracking,
 * deletion, and snoozing on the Alerts &amp; Notifications page.
 *
 * <p>All write endpoints require an authenticated principal and redirect to
 * {@code /alerts} on success or failure.</p>
 */
@Controller
public class BillController {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillService billService;

    /**
     * Creates a new bill for the authenticated user.
     *
     * <p>If the bill is one-time and the user has added a bill with the same name
     * before, a suggestion to make it recurring monthly is shown.</p>
     *
     * @param billName           display name of the bill
     * @param amountType         {@code "fixed"} or {@code "variable"}
     * @param amount             the known/fixed dollar amount
     * @param estimatedAmount    estimated amount (only used when type is variable)
     * @param dueDate            due date in {@code YYYY-MM-DD} format
     * @param frequency          {@code "one-time"}, {@code "weekly"}, {@code "monthly"}, or {@code "yearly"}
     * @param category           spending category label
     * @param notes              optional free-text notes
     * @param reminderBefore     days-before-due to send reminders
     * @param overdueAfter       days-after-due to send overdue reminders
     * @param principal          the currently authenticated user
     * @param redirectAttributes flash attributes for the redirect response
     * @return redirect to {@code /alerts}
     */
    @PostMapping("/alerts/bills")
    public String createBill(
            @RequestParam String billName,
            @RequestParam(defaultValue = "fixed") String amountType,
            @RequestParam(defaultValue = "0") double amount,
            @RequestParam(required = false) String estimatedAmount,
            @RequestParam String dueDate,
            @RequestParam(defaultValue = "one-time") String frequency,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) List<Integer> reminderBefore,
            @RequestParam(required = false) List<Integer> overdueAfter,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (principal == null) return "redirect:/login";
        try {
            Bill bill = new Bill();
            bill.setUsername(principal.getName());
            applyFields(bill, billName, amountType, amount, estimatedAmount, dueDate, frequency,
                    category, notes, reminderBefore, overdueAfter);
            bill.setCreatedAt(LocalDateTime.now());
            bill.setUpdatedAt(LocalDateTime.now());
            billRepository.save(bill);

            redirectAttributes.addFlashAttribute("billMessage", "Bill created successfully.");

            if ("one-time".equalsIgnoreCase(frequency)
                    && billService.suggestRecurringMonthly(principal.getName(), billName)) {
                redirectAttributes.addFlashAttribute("billSuggestion",
                        "You have added this bill multiple times. Consider making it recurring monthly.");
            }
        } catch (DateTimeParseException ex) {
            redirectAttributes.addFlashAttribute("billError", "Invalid due date. Please select a valid date.");
        }
        return "redirect:/alerts";
    }

    /**
     * Updates an existing bill owned by the authenticated user.
     *
     * @param id                 the bill's MongoDB document ID
     * @param billName           updated bill name
     * @param amountType         {@code "fixed"} or {@code "variable"}
     * @param amount             updated fixed amount
     * @param estimatedAmount    updated estimated amount (variable bills only)
     * @param dueDate            updated due date in {@code YYYY-MM-DD} format
     * @param frequency          updated recurrence frequency
     * @param category           updated spending category
     * @param notes              updated notes
     * @param reminderBefore     updated pre-due reminder days
     * @param overdueAfter       updated overdue reminder days
     * @param principal          the currently authenticated user
     * @param redirectAttributes flash attributes for the redirect response
     * @return redirect to {@code /alerts}
     */
    @PostMapping("/alerts/bills/{id}/edit")
    public String editBill(
            @PathVariable String id,
            @RequestParam String billName,
            @RequestParam(defaultValue = "fixed") String amountType,
            @RequestParam(defaultValue = "0") double amount,
            @RequestParam(required = false) String estimatedAmount,
            @RequestParam String dueDate,
            @RequestParam(defaultValue = "one-time") String frequency,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) List<Integer> reminderBefore,
            @RequestParam(required = false) List<Integer> overdueAfter,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (principal == null) return "redirect:/login";

        Bill bill = billRepository.findByIdAndUsername(id, principal.getName()).orElse(null);
        if (bill == null) {
            redirectAttributes.addFlashAttribute("billError", "Bill not found.");
            return "redirect:/alerts";
        }

        try {
            applyFields(bill, billName, amountType, amount, estimatedAmount, dueDate, frequency,
                    category, notes, reminderBefore, overdueAfter);
            bill.setUpdatedAt(LocalDateTime.now());
            billRepository.save(bill);
            redirectAttributes.addFlashAttribute("billMessage", "Bill updated successfully.");
        } catch (DateTimeParseException ex) {
            redirectAttributes.addFlashAttribute("billError", "Invalid due date. Please select a valid date.");
            return "redirect:/alerts?editBillId=" + id;
        }

        return "redirect:/alerts";
    }

    /**
     * Marks a bill as paid and records the payment amount.
     *
     * @param id                 the bill's MongoDB document ID
     * @param paidAmount         the amount actually paid (optional; uses bill amount if omitted)
     * @param principal          the currently authenticated user
     * @param redirectAttributes flash attributes for the redirect response
     * @return redirect to {@code /alerts}
     */
    @PostMapping("/alerts/bills/{id}/paid")
    public String markPaid(
            @PathVariable String id,
            @RequestParam(required = false) String paidAmount,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (principal == null) return "redirect:/login";

        Bill bill = billRepository.findByIdAndUsername(id, principal.getName()).orElse(null);
        if (bill == null) {
            redirectAttributes.addFlashAttribute("billError", "Bill not found.");
            return "redirect:/alerts";
        }

        Double amount = parseOptionalDouble(paidAmount);
        billService.markAsPaid(bill, amount);
        redirectAttributes.addFlashAttribute("billMessage", "Payment recorded.");
        return "redirect:/alerts";
    }

    /**
     * Reverts a bill's payment status back to unpaid.
     *
     * @param id                 the bill's MongoDB document ID
     * @param principal          the currently authenticated user
     * @param redirectAttributes flash attributes for the redirect response
     * @return redirect to {@code /alerts}
     */
    @PostMapping("/alerts/bills/{id}/unpaid")
    public String markUnpaid(
            @PathVariable String id,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (principal == null) return "redirect:/login";

        Bill bill = billRepository.findByIdAndUsername(id, principal.getName()).orElse(null);
        if (bill == null) {
            redirectAttributes.addFlashAttribute("billError", "Bill not found.");
            return "redirect:/alerts";
        }

        billService.markAsUnpaid(bill);
        redirectAttributes.addFlashAttribute("billMessage", "Marked as unpaid.");
        return "redirect:/alerts";
    }

    /**
     * Permanently deletes a bill owned by the authenticated user.
     *
     * @param id                 the bill's MongoDB document ID
     * @param principal          the currently authenticated user
     * @param redirectAttributes flash attributes for the redirect response
     * @return redirect to {@code /alerts}
     */
    @PostMapping("/alerts/bills/{id}/delete")
    public String deleteBill(
            @PathVariable String id,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (principal == null) return "redirect:/login";
        Bill bill = billRepository.findByIdAndUsername(id, principal.getName()).orElse(null);
        if (bill != null) {
            billRepository.delete(bill);
            redirectAttributes.addFlashAttribute("billMessage", "Bill deleted.");
        }
        return "redirect:/alerts";
    }

    /**
     * Snoozes a bill reminder by the specified number of days.
     *
     * @param id                 the bill's MongoDB document ID
     * @param snoozeDays         number of days to snooze (defaults to 1)
     * @param principal          the currently authenticated user
     * @param redirectAttributes flash attributes for the redirect response
     * @return redirect to {@code /alerts}
     */
    @PostMapping("/alerts/bills/{id}/snooze")
    public String snoozeBill(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int snoozeDays,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (principal == null) return "redirect:/login";
        Bill bill = billRepository.findByIdAndUsername(id, principal.getName()).orElse(null);
        if (bill == null) {
            redirectAttributes.addFlashAttribute("billError", "Bill not found.");
            return "redirect:/alerts";
        }
        billService.snooze(bill, snoozeDays);
        redirectAttributes.addFlashAttribute("billMessage", "Reminder snoozed.");
        return "redirect:/alerts";
    }

    private void applyFields(Bill bill,
                             String billName,
                             String amountType,
                             double amount,
                             String estimatedAmount,
                             String dueDate,
                             String frequency,
                             String category,
                             String notes,
                             List<Integer> reminderBefore,
                             List<Integer> overdueAfter) {

        bill.setBillName(billName != null ? billName.trim() : "");
        bill.setAmountType(normalizeAmountType(amountType));
        bill.setAmount(Math.max(amount, 0));
        bill.setEstimatedAmount(parseOptionalDouble(estimatedAmount));
        bill.setDueDate(LocalDate.parse(dueDate));
        bill.setFrequency(normalizeFrequency(frequency));
        bill.setCategory(category != null ? category.trim() : "");
        bill.setNotes(notes != null ? notes.trim() : "");

        List<Integer> before = billService.normalizeBefore(reminderBefore);
        List<Integer> overdue = billService.normalizeOverdue(overdueAfter);
        bill.setReminderDaysBefore(new ArrayList<>(before));
        bill.setOverdueReminderDaysAfter(new ArrayList<>(overdue));

        if (!"variable".equalsIgnoreCase(bill.getAmountType())) {
            bill.setEstimatedAmount(null);
        }
    }

    private String normalizeAmountType(String raw) {
        if (raw == null) return "fixed";
        return raw.equalsIgnoreCase("variable") ? "variable" : "fixed";
    }

    private String normalizeFrequency(String raw) {
        if (raw == null) return "one-time";
        String f = raw.trim().toLowerCase();
        if (f.equals("weekly") || f.equals("monthly") || f.equals("yearly")) return f;
        return "one-time";
    }

    private Double parseOptionalDouble(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
