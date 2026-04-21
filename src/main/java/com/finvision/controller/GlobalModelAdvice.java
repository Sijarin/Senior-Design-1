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

@ControllerAdvice
public class GlobalModelAdvice {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BillService billService;

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
