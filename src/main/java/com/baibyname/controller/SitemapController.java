package com.baibyname.controller;

import com.baibyname.repository.GivenNameRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Controller for sitemap.xml generation.
 * Provides XML sitemap with all name pages for SEO.
 * Splits sitemaps into chunks of 50,000 URLs to comply with Google's limit.
 */
@Controller
public class SitemapController {

    private final GivenNameRepository givenNameRepository;

    // Google's limit for URLs per sitemap file
    private static final int URLS_PER_SITEMAP = 50000;

    public SitemapController(GivenNameRepository givenNameRepository) {
        this.givenNameRepository = givenNameRepository;
    }

    /**
     * Generate sitemap index that references all sitemap chunks.
     *
     * @return sitemap index XML content
     */
    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    @ResponseBody
    public String sitemapIndex() {
        long totalNames = givenNameRepository.count();
        int totalSitemaps = (int) Math.ceil((double) totalNames / URLS_PER_SITEMAP);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        for (int i = 0; i < totalSitemaps; i++) {
            xml.append("  <sitemap>\n");
            xml.append("    <loc>https://example.com/sitemap-").append(i).append(".xml</loc>\n");
            xml.append("    <lastmod>").append(OffsetDateTime.now().toLocalDate()).append("</lastmod>\n");
            xml.append("  </sitemap>\n");
        }

        xml.append("</sitemapindex>\n");

        return xml.toString();
    }

    /**
     * Generate a specific sitemap chunk by index.
     *
     * @param index the chunk index (0-based)
     * @return XML sitemap content for this chunk
     */
    @GetMapping(value = "/sitemap-{index}.xml", produces = "application/xml")
    @ResponseBody
    public String sitemapChunk(@PathVariable int index) {
        Pageable pageable = PageRequest.of(index, URLS_PER_SITEMAP);
        List<String> names = givenNameRepository.findAllChunk(pageable);

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
