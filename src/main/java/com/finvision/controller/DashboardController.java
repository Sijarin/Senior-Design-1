package com.finvision.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
    
    @GetMapping("/")
    public String home() {
        return "Dashboard";
    }
    
    @GetMapping("/dashboard")
    public String dashboard() {
        return "Dashboard";
    }
    
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}