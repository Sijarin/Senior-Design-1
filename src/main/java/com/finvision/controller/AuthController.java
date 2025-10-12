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

    @GetMapping("/login")
    public String login() {
        return "login"; // loads login.html
    }

    @GetMapping("/register")
    public String register() {
        return "register"; // loads register.html
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username,
                               @RequestParam String password,
                               @RequestParam(required = false) String email,
                               @RequestParam(required = false) String confirmPassword,
                               Model model) {

        // Validate passwords match (if you're using confirmPassword field)
        if (confirmPassword != null && !password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match");
            return "register";
        }

        // Check if username already exists
        if (userRepository.findByUsername(username).isPresent()) {
            model.addAttribute("error", "Username already exists");
            return "register";
        }

        // Create new user with hashed password
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password)); // Hash the password!

        /*// Set email if your User model has it
        if (email != null && !email.isEmpty()) {
            user.setEmail(email);
        }*/

        userRepository.save(user);

        model.addAttribute("message", "Account created successfully! You can now login.");
        return "redirect:/login?registered=true";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password"; // loads forgot-password.html
    }

    @GetMapping("/reset-password")
    public String resetPassword() {
        return "reset-password"; // loads reset-password.html
    }
}
