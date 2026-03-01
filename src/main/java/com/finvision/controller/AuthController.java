package com.finvision.controller;

import com.finvision.model.User;
import com.finvision.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- LOGIN ----------
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // ---------- REGISTER ----------
    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username,
                               @RequestParam String email,
                               @RequestParam String password,
                               @RequestParam(required = false) String confirmPassword,
                               @RequestParam(required = false) String securityQuestion,
                               @RequestParam(required = false) String securityAnswer,
                               Model model) {

        if (confirmPassword != null && !password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            return "register";
        }

        if (userRepository.findByUsername(username).isPresent()) {
            model.addAttribute("error", "Username already exists.");
            return "register";
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setSecurityQuestion(securityQuestion);
        user.setSecurityAnswer(securityAnswer);
        userRepository.save(user);

        return "redirect:/login?registered=true";
    }

    // ---------- FORGOT PASSWORD ----------
    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("username") String username, Model model) {
        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            model.addAttribute("error", "No user found with that username.");
            return "forgot-password";
        }

        model.addAttribute("securityQuestion", user.getSecurityQuestion());
        model.addAttribute("username", username);
        return "reset-password";
    }

    // ---------- RESET PASSWORD ----------
    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam("username") String username,
                                       @RequestParam("securityAnswer") String securityAnswer,
                                       @RequestParam("newPassword") String newPassword,
                                       Model model) {

        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null || !user.getSecurityAnswer().equalsIgnoreCase(securityAnswer)) {
            model.addAttribute("error", "Incorrect security answer.");
            model.addAttribute("username", username);
            model.addAttribute("securityQuestion", user != null ? user.getSecurityQuestion() : "");
            return "reset-password";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        model.addAttribute("success", "Password reset successful! You can now log in.");
        return "login";
    }

    // ---------- PROFILE UPDATE ----------
    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam String name,
                                @RequestParam String email,
                                @RequestParam String username,
                                Authentication authentication, Model model) {

        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) return "redirect:/login";

        user.setName(name);
        user.setEmail(email);
        user.setUsername(username);
        userRepository.save(user);

        model.addAttribute("user", user);
        model.addAttribute("success", "Profile updated successfully.");
        return "profile";
    }

    // ---------- PASSWORD CHANGE ----------
    @PostMapping("/profile/password")
    public String updatePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Authentication authentication, Model model) {

        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) return "redirect:/login";

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            model.addAttribute("user", user);
            model.addAttribute("error", "Current password is incorrect.");
            return "profile";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("user", user);
            model.addAttribute("error", "New passwords do not match.");
            return "profile";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        model.addAttribute("user", user);
        model.addAttribute("success", "Password updated successfully.");
        return "profile";
    }

    // ---------- DELETE ACCOUNT ----------
    @PostMapping("/profile/delete")
    public String deleteAccount(Authentication authentication, HttpServletRequest request) {
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user != null) {
            userRepository.delete(user);
        }
        request.getSession().invalidate();
        return "redirect:/login?deleted=true";
    }
}
