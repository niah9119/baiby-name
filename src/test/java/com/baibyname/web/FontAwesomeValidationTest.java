package com.baibyname.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that verify all Font Awesome icons used in templates exist in the loaded
 * Font Awesome 6.5.0 stylesheet.
 * <p>
 * This test addresses issue #127: "The Remove button has a third defect: its icon does not exist."
 * The fa-heart-crush icon was not a valid Font Awesome 6 icon, causing the button to render
 * with empty space where the icon should be.
 */
class FontAwesomeValidationTest {

    private static final Path TEMPLATE_DIR = Paths.get("src/main/resources/templates");
    private static final Path ICONS_FILE = Paths.get("src/test/resources/font-awesome-icons-6.5.0.txt");
    private static Set<String> VALID_ICONS = new HashSet<>();

    /**
     * Load the Font Awesome icon names from the local resource file.
     */
    @BeforeAll
    static void loadValidIcons() throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(ICONS_FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    VALID_ICONS.add(line);
                }
            }
        }

        assertThat(VALID_ICONS).as("Should have loaded Font Awesome icons from resource file").isNotEmpty();
    }

    /**
     * Test that every Font Awesome icon class used in templates exists in the loaded
     * Font Awesome 6.5.0 stylesheet.
     */
    @Test
    void allFontAwesomeIconsExistInResourceFile() throws Exception {
        List<String> templateFiles = Files.list(TEMPLATE_DIR)
                .filter(p -> p.toString().endsWith(".html"))
                .map(p -> {
                    try {
                        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        assertThat(templateFiles).as("Should find template files").isNotEmpty();

        Set<String> usedIcons = new HashSet<>();
        Pattern iconPattern = Pattern.compile("fa-solid fa-([a-z0-9-]+)");

        for (String content : templateFiles) {
            Matcher matcher = iconPattern.matcher(content);
            while (matcher.find()) {
                usedIcons.add(matcher.group(1));
            }
        }

        // Check each used icon against the valid set
        for (String icon : usedIcons) {
            assertThat(VALID_ICONS).as("Icon 'fa-solid fa-" + icon + "' should exist in Font Awesome 6.5.0")
                    .contains(icon);
        }

        // Specifically verify the Remove button uses a valid icon
        assertThat(usedIcons).as("Should use fa-heart-crack for the Remove button")
                .contains("heart-crack");
    }

    /**
     * Test that no template uses the invalid fa-heart-crush icon.
     */
    @Test
    void noHeartCrushIcon() throws Exception {
        List<String> templateFiles = Files.list(TEMPLATE_DIR)
                .filter(p -> p.toString().endsWith(".html"))
                .map(p -> {
                    try {
                        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        for (String content : templateFiles) {
            assertThat(content).as("Template should not contain fa-heart-crush")
                    .doesNotContain("fa-heart-crush");
        }
    }
}
