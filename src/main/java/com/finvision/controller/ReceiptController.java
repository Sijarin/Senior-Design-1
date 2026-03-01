package com.finvision.controller;

import com.finvision.model.ScannedReceipt;
import com.finvision.repository.ScannedReceiptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Controller
public class ReceiptController {

    @Autowired
    private ScannedReceiptRepository receiptRepository;

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
        return "ReceiptScan";
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

        // Save optional thumbnail (resize happens client-side via canvas)
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

    /** Delete a receipt by ID (only if owned by the logged-in user) */
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
