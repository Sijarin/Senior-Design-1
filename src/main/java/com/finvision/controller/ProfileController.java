package com.finvision.controller;

import com.finvision.model.User;
import com.finvision.repository.BudgetRepository;
import com.finvision.repository.ScannedReceiptRepository;
import com.finvision.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles the user profile page: viewing account info, changing the password,
 * uploading a profile photo, and deleting the account.
 */
@Controller
public class ProfileController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private ScannedReceiptRepository receiptRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Displays the user's profile page.
     *
     * <p>Auto-generates a 10-digit account number and routing number on the
     * first visit if they have not been set yet.</p>
     *
     * @param principal the currently authenticated user
     * @param model     Spring MVC model
     * @return the {@code profile} template, or redirect to {@code /login} if unauthenticated
     */
    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        if (principal == null) return "redirect:/login";
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return "redirect:/login";

        // Auto-generate unique 10-digit numbers on first visit
        boolean changed = false;
        if (user.getAccountNumber() == null || user.getAccountNumber().isBlank()) {
            user.setAccountNumber(String.format("%010d",
                ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L)));
            changed = true;
        }
        if (user.getRoutingNumber() == null || user.getRoutingNumber().isBlank()) {
            user.setRoutingNumber(String.format("%010d",
                ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L)));
            changed = true;
        }
        if (changed) userRepository.save(user);

        model.addAttribute("user", user);
        return "profile";
    }

    /**
     * Refreshes the profile page after an update action.
     *
     * @param principal the currently authenticated user
     * @param model     Spring MVC model
     * @return the {@code profile} template
     */
    @PostMapping("/profile/update")
    public String updateProfile(Principal principal, Model model) {
        if (principal == null) return "redirect:/login";
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return "redirect:/login";
        model.addAttribute("user", user);
        return "profile";
    }

    /**
     * Changes the authenticated user's password.
     *
     * <p>Verifies the current password, confirms the new password matches its
     * confirmation, and enforces a minimum length of 6 characters before saving.</p>
     *
     * @param currentPassword the user's existing password
     * @param newPassword     the desired new password
     * @param confirmPassword confirmation of the new password
     * @param principal       the currently authenticated user
     * @param model           Spring MVC model
     * @return the {@code profile} template with a success or error message
     */
    @PostMapping("/profile/password")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Principal principal, Model model) {

        if (principal == null) return "redirect:/login";
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return "redirect:/login";

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            model.addAttribute("user", user);
            model.addAttribute("passwordError", "Current password is incorrect.");
            return "profile";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("user", user);
            model.addAttribute("passwordError", "New passwords do not match.");
            return "profile";
        }
        if (newPassword.length() < 6) {
            model.addAttribute("user", user);
            model.addAttribute("passwordError", "New password must be at least 6 characters.");
            return "profile";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        model.addAttribute("user", user);
        model.addAttribute("passwordSuccess", "Password changed successfully!");
        return "profile";
    }

    /**
     * Uploads and saves a new profile photo for the authenticated user.
     *
     * <p>The image is Base64-encoded and stored inline in the user document as a
     * data URL (e.g., {@code data:image/jpeg;base64,...}).</p>
     *
     * @param photo     the uploaded image file
     * @param principal the currently authenticated user
     * @param model     Spring MVC model
     * @return the {@code profile} template with a success or error message
     */
    @PostMapping("/profile/photo")
    public String uploadPhoto(
            @RequestParam("photo") MultipartFile photo,
            Principal principal, Model model) {

        if (principal == null) return "redirect:/login";
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return "redirect:/login";

        if (!photo.isEmpty()) {
            try {
                String contentType = photo.getContentType();
                if (contentType == null) contentType = "image/jpeg";
                String base64 = Base64.getEncoder().encodeToString(photo.getBytes());
                user.setProfilePhoto("data:" + contentType + ";base64," + base64);
                userRepository.save(user);
                model.addAttribute("photoSuccess", "Profile photo updated!");
            } catch (Exception e) {
                model.addAttribute("photoError", "Failed to upload photo. Please try again.");
            }
        }

        model.addAttribute("user", user);
        return "profile";
    }

    /**
     * Permanently deletes the authenticated user's account and all associated data.
     *
     * <p>Erases all budget documents, scanned receipts, and the user document from
     * MongoDB, then invalidates the HTTP session and clears the Spring Security
     * context to log the user out immediately.</p>
     *
     * @param principal the currently authenticated user
     * @param request   the HTTP request used to invalidate the session
     * @return redirect to {@code /login?deleted}
     */
    @PostMapping("/profile/delete")
    public String deleteAccount(Principal principal, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        String username = principal.getName();

        // Erase all data for this user
        budgetRepository.deleteByUsername(username);
        receiptRepository.deleteByUsername(username);
        userRepository.findByUsername(username).ifPresent(userRepository::delete);

        // Invalidate session and clear security context (logs the user out)
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();

        return "redirect:/login?deleted";
    }
}
