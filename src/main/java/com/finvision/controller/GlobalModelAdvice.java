package com.finvision.controller;

import com.finvision.model.User;
import com.finvision.repository.UserRepository;
import com.finvision.service.BillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.List;

/**
 * Injects common model attributes into every controller response.
 *
 * <p>Before each request that produces a view, this advice adds the logged-in
 * user's profile photo, display name, username initial, and the bell-notification
 * items and count to the model so every Thymeleaf template can render them
 * without each controller having to fetch them individually.</p>
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BillService billService;

    /**
     * Populates shared user and notification attributes for every view.
     *
     * <p>Added attributes when a user is authenticated:
     * <ul>
     *   <li>{@code profilePhoto} — Base64 data URL of the user's avatar</li>
     *   <li>{@code userInitial} — first letter of the display name (uppercase)</li>
     *   <li>{@code displayName} — full name if set, otherwise the username</li>
     *   <li>{@code loggedInUsername} — the raw username string</li>
     *   <li>{@code bellItems} — list of upcoming/overdue bill reminders</li>
     *   <li>{@code bellCount} — count of active bell notifications</li>
     * </ul>
     *
     * @param principal the currently authenticated user, or {@code null} if anonymous
     * @param model     Spring MVC model to populate
     */
    @ModelAttribute
    public void addUserAttributes(Principal principal, Model model) {
        if (principal != null) {
            User user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user != null) {
                model.addAttribute("profilePhoto", user.getProfilePhoto());
                String displayName = (user.getName() != null && !user.getName().isBlank())
                        ? user.getName() : user.getUsername();
                model.addAttribute("userInitial", displayName.substring(0, 1).toUpperCase());
                model.addAttribute("displayName", displayName);
                model.addAttribute("loggedInUsername", user.getUsername());
            }
            List<BillService.BellItem> bellItems = billService.buildBellItems(principal.getName());
            model.addAttribute("bellItems", bellItems);
            model.addAttribute("bellCount", bellItems.size());
        }
    }
}
