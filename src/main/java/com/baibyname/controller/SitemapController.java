package com.baibyname.controller;

import com.baibyname.repository.GivenNameRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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
 *
 * <p>Share links (/s/**) are intentionally excluded from sitemaps to prevent
 * indexing of personal shortlists shared via email. These pages carry
 * noindex,nofollow meta tags as additional protection.</p>
 */
@Controller
public class SitemapController {

    private final GivenNameRepository givenNameRepository;

    @Value("${app.base-url}")
    private String baseUrl;

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
            xml.append("    <loc>").append(baseUrl).append("/sitemap-").append(i).append(".xml</loc>\n");
            xml.append("    <lastmod>").append(OffsetDateTime.now().toLocalDate()).append("</lastmod>\n");
            xml.append("  </sitemap>\n");
        }

        xml.append("</sitemapindex>\n");

        return xml.toString();
    }

    /**
     * Generate a specific sitemap chunk by index.
     * Only includes name pages (/names/**), not share links (/s/**).
     *
     * <p>The rendered chunk is cached indefinitely to avoid rebuilding the same content
     * on every request. The cache is keyed by the chunk index and invalidated when
     * GivenName entities are modified (handled by spring-boot-starter-cache and
     * cache evictions on name repository operations).</p>
     *
     * @param index the chunk index (0-based)
     * @return XML sitemap content for this chunk
     */
    @GetMapping(value = "/sitemap-{index}.xml", produces = "application/xml")
    @ResponseBody
    @Cacheable(cacheNames = "sitemapChunks", key = "#index")
    public String sitemapChunk(@PathVariable int index) {
        Pageable pageable = PageRequest.of(index, URLS_PER_SITEMAP);
        List<String> names = givenNameRepository.findAllChunk(pageable);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        for (String name : names) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(baseUrl).append("/names/").append(escapeXml(name)).append("</loc>\n");
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
