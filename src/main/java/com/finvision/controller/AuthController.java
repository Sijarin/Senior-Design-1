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

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- LOGIN ----------
    @GetMapping("/login")
    public String login() {
        return "login"; // loads login.html
    }

    // ---------- REGISTER ----------
    @GetMapping("/register")
    public String register() {
        return "register"; // loads register.html
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username,
                               @RequestParam String password,
                               @RequestParam(required = false) String confirmPassword,
                               Model model) {

        // Check if passwords match
        if (confirmPassword != null && !password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            return "register";
        }

        // Check if username exists
        if (userRepository.findByUsername(username).isPresent()) {
            model.addAttribute("error", "Username already exists.");
            return "register";
        }

        // Save new user
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));

        // TODO: Set security question/answer if added in register.html
        // user.setSecurityQuestion(securityQuestion);
        // user.setSecurityAnswer(securityAnswer);

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
}
