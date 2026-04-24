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

/**
 * Handles user authentication flows: registration, login, and password reset.
 *
 * <p>The password-reset flow is a three-step process:
 * <ol>
 *   <li>User submits their username ({@code /forgot-password}).</li>
 *   <li>User verifies identity via PIN or security question ({@code /verify-identity}).</li>
 *   <li>User sets a new password ({@code /reset-password}).</li>
 * </ol>
 * The verified username is held in the HTTP session between steps 2 and 3.
 */
@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Redirects the root URL to the login page.
     *
     * @return redirect to {@code /login}
     */
    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    /**
     * Displays the login page.
     *
     * @return the {@code login} template name
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * Displays the registration page.
     *
     * @return the {@code register} template name
     */
    @GetMapping("/register")
    public String register() {
        return "register";
    }

    /**
     * Processes the registration form.
     *
     * <p>Validates that passwords match, that the username is not already taken,
     * and that the PIN is exactly 4 digits. On success, saves the new user with
     * BCrypt-hashed password and PIN, then redirects to the login page.</p>
     *
     * @param username         chosen username
     * @param password         chosen password
     * @param confirmPassword  password confirmation (optional)
     * @param email            optional email address
     * @param firstName        optional first name
     * @param lastName         optional last name
     * @param pin              4-digit numeric PIN for account recovery
     * @param securityQuestion optional security question for password reset
     * @param securityAnswer   answer to the security question (stored lowercase)
     * @param model            Spring MVC model for error messages
     * @return redirect to {@code /login?registered=true} on success, or back to
     *         the registration page with an error message
     */
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

    /**
     * Displays the "Forgot Password" page (Step 1 of password reset).
     *
     * @return the {@code forgot-password} template name
     */
    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "forgot-password";
    }

    /**
     * Processes the username submission for password reset (Step 1).
     *
     * <p>Looks up the account and, if found, forwards to the reset-password
     * page with the user's security question pre-loaded for Step 2.</p>
     *
     * @param username the username whose password should be reset
     * @param model    Spring MVC model
     * @return the {@code reset-password} template (Step 2 view) or back to
     *         {@code forgot-password} with an error if the username is not found
     */
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

    /**
     * Verifies the user's identity via PIN or security-question answer (Step 2).
     *
     * <p>On success, stores the username in the HTTP session under the key
     * {@code pendingReset} so Step 3 can authorize the password change without
     * requiring the user to re-verify.</p>
     *
     * @param username       the username being verified
     * @param verifyMethod   either {@code "pin"} or {@code "question"}
     * @param pinValue       the 4-digit PIN (required when {@code verifyMethod} is {@code "pin"})
     * @param securityAnswer the answer to the security question (required when method is {@code "question"})
     * @param request        the HTTP request (used to access the session)
     * @param model          Spring MVC model
     * @return the {@code reset-password} template advanced to Step 3 on success,
     *         or Step 2 again with an error message on failure
     */
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

    /**
     * Displays the reset-password page. If a {@code pendingReset} session attribute
     * is present the page renders in Step 3 (new-password entry) mode.
     *
     * @param request the HTTP request (checked for the {@code pendingReset} session attribute)
     * @param model   Spring MVC model
     * @return the {@code reset-password} template
     */
    @GetMapping("/reset-password")
    public String resetPasswordPage(HttpServletRequest request, Model model) {
        String username = (String) request.getSession().getAttribute("pendingReset");
        if (username != null) {
            model.addAttribute("passwordStep", true);
        }
        return "reset-password";
    }

    /**
     * Saves the new password for the user identified by the {@code pendingReset}
     * session attribute (Step 3).
     *
     * <p>Validates that the new password is confirmed and at least 6 characters long,
     * then BCrypt-hashes and persists it. Clears the session attribute on success.</p>
     *
     * @param newPassword     the new password entered by the user
     * @param confirmPassword optional confirmation of the new password
     * @param request         HTTP request used to access and clear the session
     * @param model           Spring MVC model
     * @return redirects to {@code /forgot-password} if the session is missing; otherwise
     *         renders the login page with a success message or the reset page with an error
     */
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
