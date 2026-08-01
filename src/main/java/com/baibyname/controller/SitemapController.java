package com.baibyname.controller;

import com.baibyname.repository.GivenNameRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Controller for sitemap.xml generation.
 * Provides XML sitemap with all name pages for SEO.
 */
@Controller
public class SitemapController {

    private final GivenNameRepository givenNameRepository;

    public SitemapController(GivenNameRepository givenNameRepository) {
        this.givenNameRepository = givenNameRepository;
    }

    /**
     * Generate sitemap.xml with all name pages.
     *
     * @return XML sitemap content
     */
    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    @ResponseBody
    public String sitemap() {
        List<String> names = givenNameRepository.findAll().stream()
                .map(com.baibyname.domain.GivenName::getName)
                .toList();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        for (String name : names) {
            xml.append("  <url>\n");
            xml.append("    <loc>https://example.com/names/").append(escapeXml(name)).append("</loc>\n");
            xml.append("    <lastmod>").append(OffsetDateTime.now().toLocalDate()).append("</lastmod>\n");
            xml.append("    <priority>0.8</priority>\n");
            xml.append("  </url>\n");
        }

        xml.append("</urlset>\n");

        return xml.toString();
    }

    private String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
