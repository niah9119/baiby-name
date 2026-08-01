package com.baibyname.controller;

import com.baibyname.service.AccountService;
import com.baibyname.service.ConsentService;
import com.baibyname.web.RegistrationForm;
import com.baibyname.web.LoginForm;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Controller
public class AccountController {

    private final AccountService accountService;
    private final ConsentService consentService;

    public AccountController(AccountService accountService, ConsentService consentService) {
        this.accountService = accountService;
        this.consentService = consentService;
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "registration";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute RegistrationForm form, BindingResult bindingResult,
                               Model model) {
        // Validate password match
        if (!form.passwordsMatch()) {
            bindingResult.rejectValue("confirmPassword", "match", "Passwords do not match");
        }

        // Check if email is already registered
        if (accountService.isEmailRegistered(form.getEmail())) {
            bindingResult.rejectValue("email", "duplicate", "Email is already registered");
        }

        if (bindingResult.hasErrors()) {
            return "registration";
        }

        // Register the user
        accountService.register(form.getEmail(), form.getPassword());

        // Redirect to login page with success message
        model.addAttribute("success", true);
        return "login";
    }

    @GetMapping("/login")
    public String showLoginForm(Model model) {
        model.addAttribute("loginForm", new LoginForm());
        return "login";
    }

    @ModelAttribute("loginForm")
    public LoginForm getLoginForm() {
        return new LoginForm();
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/delete-account")
    public String showDeleteAccount() {
        return "delete-account";
    }

    @PostMapping("/delete-account")
    public String deleteAccount(HttpSession session) {
        // The actual deletion is handled by a filter or service that uses the authenticated user
        // For now, we'll just invalidate the session
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/privacy-policy")
    public String privacyPolicy() {
        return "privacy-policy";
    }

    @GetMapping("/gdpr/consent")
    public String gdprConsent() {
        return "gdpr-consent";
    }
}
