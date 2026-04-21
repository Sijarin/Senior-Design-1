package com.finvision.controller;

import com.finvision.model.User;
import com.finvision.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
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

    // ─── ROOT ────────────────────────────────────────────────────────────────
    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    // ─── LOGIN ───────────────────────────────────────────────────────────────
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // ─── REGISTER ────────────────────────────────────────────────────────────
    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String confirmPassword,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String pin,
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
        if (pin == null || !pin.matches("\\d{4}")) {
            model.addAttribute("error", "PIN must be exactly 4 digits.");
            return "register";
        }

        String fn = (firstName != null) ? firstName.trim() : "";
        String ln = (lastName  != null) ? lastName.trim()  : "";
        String fullName = (fn + " " + ln).trim();

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email != null ? email.trim() : "");
        user.setName(fullName.isEmpty() ? null : fullName);
        user.setPin(passwordEncoder.encode(pin));
        user.setSecurityQuestion(securityQuestion != null ? securityQuestion.trim() : "");
        user.setSecurityAnswer(securityAnswer != null ? securityAnswer.trim().toLowerCase() : "");

        userRepository.save(user);
        return "redirect:/login?registered=true";
    }

    // ─── FORGOT PASSWORD (Step 1: enter username) ─────────────────────────────
    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(
            @RequestParam("username") String username, Model model) {

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            model.addAttribute("error", "No account found with that username.");
            return "forgot-password";
        }

        model.addAttribute("username", username);
        model.addAttribute("securityQuestion", user.getSecurityQuestion());
        model.addAttribute("verifyStep", true);
        return "reset-password";
    }

    // ─── VERIFY IDENTITY (Step 2: PIN or security question) ──────────────────
    @PostMapping("/verify-identity")
    public String verifyIdentity(
            @RequestParam String username,
            @RequestParam String verifyMethod,
            @RequestParam(required = false) String pinValue,
            @RequestParam(required = false) String securityAnswer,
            HttpServletRequest request,
            Model model) {

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            model.addAttribute("error", "User not found.");
            model.addAttribute("verifyStep", true);
            return "reset-password";
        }

        boolean verified = false;
        if ("pin".equals(verifyMethod)) {
            verified = pinValue != null
                    && user.getPin() != null
                    && passwordEncoder.matches(pinValue, user.getPin());
        } else {
            verified = securityAnswer != null
                    && user.getSecurityAnswer() != null
                    && user.getSecurityAnswer().equalsIgnoreCase(securityAnswer.trim());
        }

        if (!verified) {
            String errMsg = "pin".equals(verifyMethod)
                    ? "Incorrect PIN. Please try again."
                    : "Incorrect security answer. Please try again.";
            model.addAttribute("error", errMsg);
            model.addAttribute("username", username);
            model.addAttribute("securityQuestion", user.getSecurityQuestion());
            model.addAttribute("verifyStep", true);
            return "reset-password";
        }

        // Verified — store in session so Step 3 can use it
        request.getSession().setAttribute("pendingReset", username);
        model.addAttribute("passwordStep", true);
        return "reset-password";
    }

    // ─── RESET PASSWORD (Step 3: set new password) ───────────────────────────
    @GetMapping("/reset-password")
    public String resetPasswordPage(HttpServletRequest request, Model model) {
        String username = (String) request.getSession().getAttribute("pendingReset");
        if (username != null) {
            model.addAttribute("passwordStep", true);
        }
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(
            @RequestParam String newPassword,
            @RequestParam(required = false) String confirmPassword,
            HttpServletRequest request,
            Model model) {

        String username = (String) request.getSession().getAttribute("pendingReset");
        if (username == null) {
            return "redirect:/forgot-password";
        }

        if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("passwordStep", true);
            return "reset-password";
        }
        if (newPassword.length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters.");
            model.addAttribute("passwordStep", true);
            return "reset-password";
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return "redirect:/forgot-password";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        request.getSession().removeAttribute("pendingReset");

        model.addAttribute("success", "Password reset successful! You can now sign in.");
        return "login";
    }
}
