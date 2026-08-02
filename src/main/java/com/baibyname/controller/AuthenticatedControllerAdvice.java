package com.baibyname.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.ui.Model;

/**
 * Controller advice that exposes an {@code authenticated} boolean attribute to all templates.
 *
 * <p>This is used as a replacement for the Thymeleaf 3.1 removed expressions
 * {@code #httpServletRequest.userPrincipal}, {@code #httpSession}, {@code #request}, and
 * {@code #session}. Those servlet expression objects were removed in Thymeleaf 3.1,
 * and this advice provides a clean way to check authentication status in templates
 * without requiring the thymeleaf-extras-springsecurity6 dependency.</p>
 */
@ControllerAdvice
public class AuthenticatedControllerAdvice {

    /**
     * Add the authenticated status to the model for all controllers.
     *
     * @param model the Thymeleaf model
     */
    public void addToModel(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated();
        model.addAttribute("authenticated", authenticated);
    }
}
