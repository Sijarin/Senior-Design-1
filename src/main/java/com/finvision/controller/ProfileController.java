package com.finvision.controller;

import com.finvision.model.User;
import com.finvision.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

@Controller
public class ProfileController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @PostMapping("/profile/update")
    public String updateProfile(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            Principal principal, Model model) {

        if (principal == null) return "redirect:/login";
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return "redirect:/login";

        user.setName(name != null ? name.trim() : "");
        user.setEmail(email != null ? email.trim() : "");
        // accountNumber and routingNumber are auto-generated and never overwritten
        userRepository.save(user);

        model.addAttribute("user", user);
        model.addAttribute("updateSuccess", "Profile updated successfully!");
        return "profile";
    }

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
}
