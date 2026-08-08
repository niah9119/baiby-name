package com.baibyname.i18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Thymeleaf message keys from template files.
 * Extracts keys from patterns like {@code #{key}} or {@code #{key(${arg})}}.
 */
class TemplateMessageKeyParser {

    private static final Pattern MESSAGE_PATTERN = Pattern.compile("#\\{([^}]+)}");

    /**
     * Parses all message keys from template files in the given directory.
     * @param templatesDir the directory containing template files
     * @return set of unique message keys found in the templates
     * @throws IOException if the directory cannot be read
     */
    static Set<String> parseKeysFromTemplates(Path templatesDir) throws IOException {
        Set<String> keys = new HashSet<>();

        if (!Files.exists(templatesDir)) {
            throw new IOException("Templates directory does not exist: " + templatesDir);
        }

        Files.walk(templatesDir)
            .filter(path -> path.toString().endsWith(".html"))
            .forEach(path -> {
                try {
                    String content = Files.readString(path);
                    extractKeysFromContent(content, keys);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read template file: " + path, e);
                }
            });

        return keys;
    }

    /**
     * Extracts message keys from template content.
     * Only extracts static keys - skips dynamic Thymeleaf expressions like
     * #{${'filter.sex.' + sex.toLowerCase()}} which are constructed at runtime.
     * @param content the template content
     * @param keys the set to add keys to
     */
    static void extractKeysFromContent(String content, Set<String> keys) {
        Matcher matcher = MESSAGE_PATTERN.matcher(content);
        while (matcher.find()) {
            String fullMatch = matcher.group(1);
            // Extract the base key from parameterized keys like "key(${arg})"
            String baseKey = extractBaseKey(fullMatch);
            // Skip dynamic keys (contain '${' or '+') - these are constructed at runtime
            if (!baseKey.isEmpty() && !baseKey.contains("${") && !baseKey.contains("+")) {
                keys.add(baseKey);
            }
        }
    }

    /**
     * Extracts the base key from a message reference.
     * For example: "shortlist.entries.count(${entries.size()}" -> "shortlist.entries.count"
     * @param reference the full message reference
     * @return the base key, or empty string if invalid
     */
    static String extractBaseKey(String reference) {
        // Handle parameterized keys like "key(${arg})"
        int parenIndex = reference.indexOf('(');
        if (parenIndex > 0) {
            return reference.substring(0, parenIndex).trim();
        }
        // Handle simple keys like "key"
        return reference.trim();
    }
}
