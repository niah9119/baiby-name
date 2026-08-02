package com.baibyname.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

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
     * <p>Uses "anonymousUser" principal check (like ShortlistService) rather than
     * {@code authentication.isAuthenticated()} because Spring's
     * {@code AnonymousAuthenticationToken} returns true for isAuthenticated().</p>
     *
     * @return true if the user is authenticated (not anonymous)
     */
    @ModelAttribute("authenticated")
    public boolean authenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        // AnonymousAuthenticationToken returns isAuthenticated()=true, so check principal
        return authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal());
    }
}
