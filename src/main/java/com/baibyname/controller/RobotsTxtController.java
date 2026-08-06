package com.baibyname.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller for robots.txt generation.
 * Provides a dynamic robots.txt with the configured base URL for sitemap location.
 */
@Controller
public class RobotsTxtController {

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Generate robots.txt with the configured base URL for sitemap location.
     *
     * @return robots.txt content
     */
    @GetMapping(value = "/robots.txt", produces = "text/plain")
    @ResponseBody
    public String robotsTxt() {
        StringBuilder sb = new StringBuilder();
        sb.append("User-agent: *\n");
        sb.append("Allow: /\n");
        sb.append("\n");
        sb.append("# Disallow share links to prevent indexing of personal shortlists\n");
        sb.append("Disallow: /s/\n");
        sb.append("\n");
        sb.append("# Disallow private account pages\n");
        sb.append("Disallow: /account/\n");
        sb.append("Disallow: /gdpr/\n");
        sb.append("\n");
        sb.append("# Disallow login/registration pages\n");
        sb.append("Disallow: /login\n");
        sb.append("Disallow: /register\n");
        sb.append("Disallow: /logout\n");
        sb.append("\n");
        sb.append("# Allow search engines to index media files\n");
        sb.append("Allow: /images/\n");
        sb.append("Allow: /css/\n");
        sb.append("Allow: /js/\n");
        sb.append("\n");
        sb.append("# Sitemap location\n");
        sb.append("Sitemap: ").append(baseUrl).append("/sitemap.xml\n");

        return sb.toString();
    }
}
