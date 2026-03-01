package com.finvision.controller;

import com.finvision.model.User;
import com.finvision.repository.UserRepository;
import com.finvision.service.BillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.Collections;

@ControllerAdvice
public class GlobalModelAdvice {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BillService billService;

    @ModelAttribute
    public void addUserAttributes(Principal principal, Model model) {
        model.addAttribute("billReminderItems", Collections.emptyList());
        model.addAttribute("billReminderCount", 0);

        if (principal != null) {
            User user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user != null) {
                model.addAttribute("profilePhoto", user.getProfilePhoto());
                String displayName = (user.getName() != null && !user.getName().isBlank())
                        ? user.getName() : user.getUsername();
                model.addAttribute("userInitial", displayName.substring(0, 1).toUpperCase());
                model.addAttribute("displayName", displayName);
            }

            var reminders = billService.buildActiveReminderItems(principal.getName());
            model.addAttribute("billReminderItems", reminders);
            model.addAttribute("billReminderCount", reminders.size());
        }
    }
}
