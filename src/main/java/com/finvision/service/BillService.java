package com.finvision.service;

import com.finvision.model.Bill;
import com.finvision.repository.BillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Core service for bill management in Finvision.
 *
 * <p>Responsibilities include:
 * <ul>
 *   <li>Building the {@link AlertsBillView} used to render the Alerts page</li>
 *   <li>Tracking payment status and recording partial/full payments</li>
 *   <li>Auto-generating the next recurring bill instance when a bill is fully paid</li>
 *   <li>Computing bell-notification items for the global navigation badge</li>
 *   <li>Evaluating active reminders for upcoming and overdue bills</li>
 * </ul>
 */
@Service
public class BillService {

    @Autowired
    private BillRepository billRepository;

    private static final DateTimeFormatter UI_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    /**
     * Returns all bills for the given user sorted by due date ascending.
     *
     * @param username the authenticated user's username
     * @return list of bills, or an empty list if the username is blank
     */
    public List<Bill> findBillsForUser(String username) {
        if (username == null || username.isBlank()) return Collections.emptyList();
        return billRepository.findByUsernameOrderByDueDateAsc(username);
    }

    /**
     * Builds the full view model for the Alerts &amp; Notifications page.
     *
     * <p>Categorizes each bill into overdue, due-soon (within 3 days), upcoming,
     * or paid-this-month buckets and computes summary statistics (counts, totals,
     * paid percentage, and a month-grouped list of all bills).</p>
     *
     * @param username the authenticated user's username
     * @return an {@link AlertsBillView} containing all display-ready data
     */
    public AlertsBillView buildAlertsBillView(String username) {
        List<Bill> bills = findBillsForUser(username);
        List<BillCard> overdue = new ArrayList<>();
        List<BillCard> dueSoon = new ArrayList<>();
        List<BillCard> upcoming = new ArrayList<>();
        List<BillCard> paidThisMonth = new ArrayList<>();
        List<BillCard> allCards = new ArrayList<>();

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);

        int dueThisMonthCount = 0;
        int paidThisMonthCount = 0;
        double dueThisWeekAmount = 0;
        double dueThisMonthAmount = 0;

        for (Bill bill : bills) {
            if (bill.getDueDate() == null) continue;

            if (YearMonth.from(bill.getDueDate()).equals(currentMonth)) {
                dueThisMonthCount++;
                if (bill.isPaid()) paidThisMonthCount++;
            }

            long daysUntil = ChronoUnit.DAYS.between(today, bill.getDueDate());
            double outstanding = Math.max(bill.getAmount() - safe(bill.getPaidAmount()), 0);

            if (!bill.isPaid()) {
                if (daysUntil >= 0 && daysUntil <= 7) dueThisWeekAmount += outstanding;
                if (YearMonth.from(bill.getDueDate()).equals(currentMonth) && daysUntil >= 0) {
                    dueThisMonthAmount += outstanding;
                }
            }

            BillStatus status;
            if (bill.isPaid()) {
                status = BillStatus.PAID;
            } else if (daysUntil < 0) {
                status = BillStatus.OVERDUE;
            } else if (daysUntil <= 3) {
                status = BillStatus.DUE_SOON;
            } else {
                status = BillStatus.UPCOMING;
            }

            BillCard card = new BillCard(
                    bill.getId(),
                    bill.getBillName(),
                    iconForCategory(bill.getCategory()),
                    formatCurrency(bill.getAmount()),
                    formatCurrency(safe(bill.getPaidAmount())),
                    formatCurrency(outstanding),
                    bill.getDueDate().format(UI_DATE),
                    bill.getDueDate().toString(),
                    dueText(daysUntil, bill.isPaid(), bill.getPaidAt()),
                    status.label,
                    status.color,
                    bill.isPaid(),
                    bill.getAmountType(),
                    bill.getFrequency(),
                    displayText(bill.getCategory(), "Uncategorized"),
                    bill.getNotes(),
                    bill.getSnoozedUntil() != null ? bill.getSnoozedUntil().format(UI_DATE) : "",
                    formatReminderList(normalizeBefore(bill.getReminderDaysBefore()), true),
                    formatReminderList(normalizeOverdue(bill.getOverdueReminderDaysAfter()), false)
            );

            if (status == BillStatus.PAID) {
                if (bill.getPaidAt() != null && YearMonth.from(bill.getPaidAt()).equals(currentMonth)) {
                    paidThisMonth.add(card);
                }
            } else if (status == BillStatus.OVERDUE) {
                overdue.add(card);
            } else if (status == BillStatus.DUE_SOON) {
                dueSoon.add(card);
            } else {
                upcoming.add(card);
            }
            allCards.add(card);
        }

        Comparator<BillCard> byDueDate = Comparator.comparing(BillCard::getDueDateRaw);
        overdue.sort(byDueDate);
        dueSoon.sort(byDueDate);
        upcoming.sort(byDueDate);
        paidThisMonth.sort(byDueDate);
        allCards.sort(byDueDate);

        java.util.Map<YearMonth, List<BillCard>> grouped = new java.util.LinkedHashMap<>();
        for (BillCard card : allCards) {
            LocalDate due = LocalDate.parse(card.getDueDateRaw());
            YearMonth ym = YearMonth.from(due);
            grouped.computeIfAbsent(ym, k -> new ArrayList<>()).add(card);
        }
        List<MonthGroup> byMonth = new ArrayList<>();
        for (java.util.Map.Entry<YearMonth, List<BillCard>> e : grouped.entrySet()) {
            byMonth.add(new MonthGroup(e.getKey().format(DateTimeFormatter.ofPattern("MMMM yyyy")), e.getValue()));
        }

        int paidPercent = dueThisMonthCount == 0 ? 0 : (int) Math.round((paidThisMonthCount * 100.0) / dueThisMonthCount);

        return new AlertsBillView(
                overdue,
                dueSoon,
                upcoming,
                paidThisMonth,
                byMonth,
                paidThisMonthCount,
                dueThisMonthCount,
                paidPercent,
                formatCurrency(dueThisWeekAmount),
                formatCurrency(dueThisMonthAmount)
        );
    }

    /**
     * Returns reminder items for bills whose pre-due or overdue reminder days
     * match today's date and that are not currently snoozed.
     *
     * @param username the authenticated user's username
     * @return list of {@link ActiveReminderItem} sorted by due date
     */
    public List<ActiveReminderItem> buildActiveReminderItems(String username) {
        List<ActiveReminderItem> items = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Bill bill : findBillsForUser(username)) {
            if (bill.getDueDate() == null || bill.isPaid()) continue;
            if (bill.getSnoozedUntil() != null && !today.isAfter(bill.getSnoozedUntil())) continue;

            List<Integer> before = normalizeBefore(bill.getReminderDaysBefore());
            List<Integer> overdue = normalizeOverdue(bill.getOverdueReminderDaysAfter());
            long daysUntil = ChronoUnit.DAYS.between(today, bill.getDueDate());

            String trigger = null;
            if (daysUntil >= 0 && before.contains((int) daysUntil)) {
                trigger = daysUntil == 0 ? "Due today" : "Due in " + daysUntil + " day(s)";
            }
            if (daysUntil < 0) {
                long overdueDays = -daysUntil;
                if (overdue.contains((int) overdueDays)) trigger = "Overdue by " + overdueDays + " day(s)";
            }

            if (trigger != null) {
                items.add(new ActiveReminderItem(
                        bill.getId(),
                        bill.getBillName(),
                        formatCurrency(Math.max(bill.getAmount() - safe(bill.getPaidAmount()), 0)),
                        bill.getDueDate().format(UI_DATE),
                        bill.getDueDate().toString(),
                        trigger
                ));
            }
        }

        items.sort(Comparator.comparing(ActiveReminderItem::getDueDateRaw));
        return items;
    }

    /**
     * Normalizes a raw list of pre-due reminder days, always including day 0
     * (the due day itself) and returning values in descending order.
     * Defaults to [7, 3, 1, 0] when the input is null or empty.
     *
     * @param raw raw reminder-days list from the form or bill document
     * @return sorted, deduplicated list of reminder days in descending order
     */
    public List<Integer> normalizeBefore(List<Integer> raw) {
        if (raw == null || raw.isEmpty()) return List.of(7, 3, 1, 0);
        TreeSet<Integer> out = new TreeSet<>();
        for (Integer v : raw) if (v != null && v >= 0) out.add(v);
        out.add(0);
        return new ArrayList<>(out.descendingSet());
    }

    /**
     * Normalizes a raw list of post-due (overdue) reminder days, excluding
     * non-positive values. Defaults to [1, 3] when the input is null or empty.
     *
     * @param raw raw overdue-days list from the form or bill document
     * @return sorted, deduplicated list of positive overdue days
     */
    public List<Integer> normalizeOverdue(List<Integer> raw) {
        if (raw == null || raw.isEmpty()) return List.of(1, 3);
        TreeSet<Integer> out = new TreeSet<>();
        for (Integer v : raw) if (v != null && v > 0) out.add(v);
        return new ArrayList<>(out);
    }

    /**
     * Records a payment against a bill and marks it fully paid when the
     * cumulative paid amount meets or exceeds the bill amount.
     *
     * <p>For variable bills the payment is also appended to the bill's
     * {@link Bill#getAmountHistory() amountHistory}. When the bill is fully
     * paid and recurring, a new bill instance for the next period is saved.</p>
     *
     * @param bill           the bill to update (must not be {@code null})
     * @param paidAmountNow  the dollar amount paid now; if {@code null} or zero,
     *                       the outstanding balance is applied
     */
    public void markAsPaid(Bill bill, Double paidAmountNow) {
        if (bill == null) return;

        double alreadyPaid = safe(bill.getPaidAmount());
        double outstanding = Math.max(bill.getAmount() - alreadyPaid, 0);
        double applied = (paidAmountNow != null && paidAmountNow > 0) ? paidAmountNow : outstanding;
        double newPaidTotal = alreadyPaid + Math.max(applied, 0);

        bill.setPaidAmount(newPaidTotal);
        bill.setUpdatedAt(LocalDateTime.now());

        if ("variable".equalsIgnoreCase(bill.getAmountType())) {
            bill.setLastMonthAmount(applied);
            List<Bill.AmountHistory> history = bill.getAmountHistory();
            if (history == null) history = new ArrayList<>();
            history.add(new Bill.AmountHistory(LocalDateTime.now(), applied));
            bill.setAmountHistory(history);
        }

        if (newPaidTotal + 0.0001 >= bill.getAmount()) {
            bill.setPaid(true);
            bill.setPaidAt(LocalDateTime.now());
            billRepository.save(bill);
            if (isRecurring(bill.getFrequency())) {
                billRepository.save(buildNextRecurringInstance(bill));
            }
        } else {
            bill.setPaid(false);
            bill.setPaidAt(null);
            billRepository.save(bill);
        }
    }

    /**
     * Resets a bill's payment status, clearing paid amount and timestamp.
     *
     * @param bill the bill to revert (must not be {@code null})
     */
    public void markAsUnpaid(Bill bill) {
        if (bill == null) return;
        bill.setPaid(false);
        bill.setPaidAmount(null);
        bill.setPaidAt(null);
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);
    }

    /**
     * Snoozes the bill's reminder by setting {@code snoozedUntil} to today plus
     * the requested number of days (minimum 1).
     *
     * @param bill the bill to snooze (must not be {@code null})
     * @param days number of days to snooze; values below 1 are treated as 1
     */
    public void snooze(Bill bill, int days) {
        if (bill == null) return;
        int safeDays = Math.max(days, 1);
        bill.setSnoozedUntil(LocalDate.now().plusDays(safeDays));
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);
    }

    /**
     * Returns {@code true} when the frequency string represents a recurring
     * schedule (weekly, monthly, or yearly).
     *
     * @param frequency the frequency value stored on a bill
     * @return {@code true} if recurring, {@code false} for one-time or unknown
     */
    public boolean isRecurring(String frequency) {
        if (frequency == null) return false;
        String f = frequency.trim().toLowerCase(Locale.ROOT);
        return f.equals("weekly") || f.equals("monthly") || f.equals("yearly");
    }

    /**
     * Calculates the next due date for a recurring bill.
     *
     * @param dueDate   the current due date
     * @param frequency the recurrence frequency ({@code "weekly"}, {@code "monthly"}, {@code "yearly"})
     * @return the advanced due date, or the original date for one-time bills
     */
    public LocalDate nextDueDate(LocalDate dueDate, String frequency) {
        if (dueDate == null || frequency == null) return dueDate;
        return switch (frequency.toLowerCase(Locale.ROOT)) {
            case "weekly" -> dueDate.plusWeeks(1);
            case "monthly" -> dueDate.plusMonths(1);
            case "yearly" -> dueDate.plusYears(1);
            default -> dueDate;
        };
    }

    private Bill buildNextRecurringInstance(Bill paidBill) {
        Bill next = new Bill();
        next.setUsername(paidBill.getUsername());
        next.setBillName(paidBill.getBillName());
        next.setAmountType(paidBill.getAmountType());
        next.setAmount(paidBill.getAmount());
        next.setEstimatedAmount(paidBill.getEstimatedAmount());
        next.setLastMonthAmount(paidBill.getLastMonthAmount());
        next.setFrequency(paidBill.getFrequency());
        next.setCategory(paidBill.getCategory());
        next.setNotes(paidBill.getNotes());
        next.setReminderDaysBefore(new ArrayList<>(normalizeBefore(paidBill.getReminderDaysBefore())));
        next.setOverdueReminderDaysAfter(new ArrayList<>(normalizeOverdue(paidBill.getOverdueReminderDaysAfter())));
        next.setDueDate(nextDueDate(paidBill.getDueDate(), paidBill.getFrequency()));
        next.setPaid(false);
        next.setPaidAmount(null);
        next.setPaidAt(null);
        next.setSnoozedUntil(null);
        next.setAutoGenerated(true);
        next.setParentBillId(paidBill.getId());
        next.setCreatedAt(LocalDateTime.now());
        next.setUpdatedAt(LocalDateTime.now());
        return next;
    }

    /**
     * Returns {@code true} when the user has added at least 2 one-time bills
     * with the same name, indicating they may want to convert it to recurring.
     *
     * @param username the authenticated user's username
     * @param billName the bill name to check (case-insensitive)
     * @return {@code true} if 2 or more matching one-time bills exist
     */
    public boolean suggestRecurringMonthly(String username, String billName) {
        if (username == null || billName == null || billName.isBlank()) return false;
        List<Bill> sameName = billRepository.findByUsernameAndBillNameIgnoreCase(username, billName.trim());
        long oneTimeCount = sameName.stream()
                .filter(b -> b.getFrequency() != null && b.getFrequency().equalsIgnoreCase("one-time"))
                .count();
        return oneTimeCount >= 2;
    }

    private String dueText(long daysUntil, boolean paid, LocalDateTime paidAt) {
        if (paid) {
            if (paidAt == null) return "Paid";
            return "Paid on " + paidAt.format(DateTimeFormatter.ofPattern("MM/dd"));
        }
        if (daysUntil < 0) return "Overdue by " + (-daysUntil) + " day(s)";
        if (daysUntil == 0) return "Due today";
        return "Due in " + daysUntil + " day(s)";
    }

    private String iconForCategory(String category) {
        String c = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (c.contains("water")) return "\uD83D\uDCA7";
        if (c.contains("electric")) return "\u26A1";
        if (c.contains("internet")) return "\uD83C\uDF10";
        if (c.contains("rent") || c.contains("housing")) return "\uD83C\uDFE0";
        return "\uD83D\uDCC4";
    }

    private String formatReminderList(List<Integer> values, boolean before) {
        if (values == null || values.isEmpty()) return before ? "Due day" : "None";
        List<String> labels = new ArrayList<>();
        for (Integer d : values) {
            if (d == null) continue;
            if (before) {
                labels.add(d == 0 ? "Due day" : d + "d before");
            } else {
                labels.add(d + "d overdue");
            }
        }
        return String.join(", ", labels);
    }

    private String displayText(String text, String fallback) {
        return (text == null || text.isBlank()) ? fallback : text;
    }

    private double safe(Double v) { return v == null ? 0.0 : v; }

    private String formatCurrency(double amount) { return String.format("$%.2f", amount); }

    private enum BillStatus {
        OVERDUE("Overdue", "red"),
        DUE_SOON("Due Soon", "yellow"),
        UPCOMING("Upcoming", "blue"),
        PAID("Paid", "green");

        private final String label;
        private final String color;

        BillStatus(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    public static class AlertsBillView {
        private final List<BillCard> overdue;
        private final List<BillCard> dueSoon;
        private final List<BillCard> upcoming;
        private final List<BillCard> paidThisMonth;
        private final List<MonthGroup> byMonth;
        private final int paidThisMonthCount;
        private final int dueThisMonthCount;
        private final int paidPercent;
        private final String dueThisWeekAmount;
        private final String dueThisMonthAmount;

        public AlertsBillView(List<BillCard> overdue, List<BillCard> dueSoon, List<BillCard> upcoming,
                              List<BillCard> paidThisMonth, List<MonthGroup> byMonth,
                              int paidThisMonthCount, int dueThisMonthCount,
                              int paidPercent, String dueThisWeekAmount, String dueThisMonthAmount) {
            this.overdue = overdue;
            this.dueSoon = dueSoon;
            this.upcoming = upcoming;
            this.paidThisMonth = paidThisMonth;
            this.byMonth = byMonth;
            this.paidThisMonthCount = paidThisMonthCount;
            this.dueThisMonthCount = dueThisMonthCount;
            this.paidPercent = paidPercent;
            this.dueThisWeekAmount = dueThisWeekAmount;
            this.dueThisMonthAmount = dueThisMonthAmount;
        }

        public List<BillCard> getOverdue() { return overdue; }
        public List<BillCard> getDueSoon() { return dueSoon; }
        public List<BillCard> getUpcoming() { return upcoming; }
        public List<BillCard> getPaidThisMonth() { return paidThisMonth; }
        public List<MonthGroup> getByMonth() { return byMonth; }
        public int getPaidThisMonthCount() { return paidThisMonthCount; }
        public int getDueThisMonthCount() { return dueThisMonthCount; }
        public int getPaidPercent() { return paidPercent; }
        public String getDueThisWeekAmount() { return dueThisWeekAmount; }
        public String getDueThisMonthAmount() { return dueThisMonthAmount; }
    }

    public static class BillCard {
        private final String id;
        private final String name;
        private final String categoryIcon;
        private final String amount;
        private final String paidAmount;
        private final String outstandingAmount;
        private final String dueDate;
        private final String dueDateRaw; // ISO yyyy-MM-dd
        private final String dueText;
        private final String statusLabel;
        private final String statusColor;
        private final boolean paid;
        private final String amountType;
        private final String frequency;
        private final String category;
        private final String notes;
        private final String snoozedUntil;
        private final String reminderBeforeText;
        private final String reminderOverdueText;

        public BillCard(String id, String name, String categoryIcon, String amount, String paidAmount,
                        String outstandingAmount, String dueDate, String dueDateRaw, String dueText, String statusLabel,
                        String statusColor, boolean paid, String amountType, String frequency,
                        String category, String notes, String snoozedUntil, String reminderBeforeText,
                        String reminderOverdueText) {
            this.id = id;
            this.name = name;
            this.categoryIcon = categoryIcon;
            this.amount = amount;
            this.paidAmount = paidAmount;
            this.outstandingAmount = outstandingAmount;
            this.dueDate = dueDate;
            this.dueDateRaw = dueDateRaw;
            this.dueText = dueText;
            this.statusLabel = statusLabel;
            this.statusColor = statusColor;
            this.paid = paid;
            this.amountType = amountType;
            this.frequency = frequency;
            this.category = category;
            this.notes = notes;
            this.snoozedUntil = snoozedUntil;
            this.reminderBeforeText = reminderBeforeText;
            this.reminderOverdueText = reminderOverdueText;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getCategoryIcon() { return categoryIcon; }
        public String getAmount() { return amount; }
        public String getPaidAmount() { return paidAmount; }
        public String getOutstandingAmount() { return outstandingAmount; }
        public String getDueDate() { return dueDate; }
        public String getDueDateRaw() { return dueDateRaw; }
        public String getDueText() { return dueText; }
        public String getStatusLabel() { return statusLabel; }
        public String getStatusColor() { return statusColor; }
        public boolean isPaid() { return paid; }
        public String getAmountType() { return amountType; }
        public String getFrequency() { return frequency; }
        public String getCategory() { return category; }
        public String getNotes() { return notes; }
        public String getSnoozedUntil() { return snoozedUntil; }
        public String getReminderBeforeText() { return reminderBeforeText; }
        public String getReminderOverdueText() { return reminderOverdueText; }
    }

    public static class MonthGroup {
        private final String label;
        private final List<BillCard> bills;

        public MonthGroup(String label, List<BillCard> bills) {
            this.label = label;
            this.bills = bills;
        }

        public String getLabel() { return label; }
        public List<BillCard> getBills() { return bills; }
    }

    /**
     * Builds the list of bell-notification items for the global navbar badge.
     * Includes unpaid bills that are due within 7 days or overdue, excluding
     * currently snoozed bills. Results are sorted by due date ascending.
     *
     * @param username the authenticated user's username
     * @return list of {@link BellItem} objects for display in the navbar dropdown
     */
    public List<BellItem> buildBellItems(String username) {
        List<BellItem> items = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Bill bill : findBillsForUser(username)) {
            if (bill.getDueDate() == null || bill.isPaid()) continue;
            if (bill.getSnoozedUntil() != null && !today.isAfter(bill.getSnoozedUntil())) continue;
            long daysUntil = ChronoUnit.DAYS.between(today, bill.getDueDate());
            if (daysUntil > 7) continue;
            String statusLabel = daysUntil < 0 ? "Overdue" : "Due Soon";
            String statusColor = daysUntil < 0 ? "red" : "yellow";
            String dueText = daysUntil < 0
                    ? "Overdue by " + (-daysUntil) + " day" + (-daysUntil == 1 ? "" : "s")
                    : daysUntil == 0 ? "Due today"
                    : "Due in " + daysUntil + " day" + (daysUntil == 1 ? "" : "s");
            items.add(new BellItem(bill.getBillName(),
                    formatCurrency(Math.max(bill.getAmount() - safe(bill.getPaidAmount()), 0)),
                    dueText, statusLabel, statusColor, bill.getDueDate().toString()));
        }
        items.sort(Comparator.comparing(BellItem::getDueDateRaw));
        return items;
    }

    public static class BellItem {
        private final String name;
        private final String amount;
        private final String dueText;
        private final String statusLabel;
        private final String statusColor;
        private final String dueDateRaw;

        public BellItem(String name, String amount, String dueText,
                        String statusLabel, String statusColor, String dueDateRaw) {
            this.name = name;
            this.amount = amount;
            this.dueText = dueText;
            this.statusLabel = statusLabel;
            this.statusColor = statusColor;
            this.dueDateRaw = dueDateRaw;
        }

        public String getName() { return name; }
        public String getAmount() { return amount; }
        public String getDueText() { return dueText; }
        public String getStatusLabel() { return statusLabel; }
        public String getStatusColor() { return statusColor; }
        public String getDueDateRaw() { return dueDateRaw; }
    }

    public static class ActiveReminderItem {
        private final String billId;
        private final String name;
        private final String amount;
        private final String dueDate;
        private final String dueDateRaw;
        private final String trigger;

        public ActiveReminderItem(String billId, String name, String amount,
                                  String dueDate, String dueDateRaw, String trigger) {
            this.billId = billId;
            this.name = name;
            this.amount = amount;
            this.dueDate = dueDate;
            this.dueDateRaw = dueDateRaw;
            this.trigger = trigger;
        }

        public String getBillId() { return billId; }
        public String getName() { return name; }
        public String getAmount() { return amount; }
        public String getDueDate() { return dueDate; }
        public String getDueDateRaw() { return dueDateRaw; }
        public String getTrigger() { return trigger; }
    }
}
