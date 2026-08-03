package com.baibyname.controller;

import com.baibyname.service.FullNameAdviceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Controller for the Full-Name Advice feature.
 *
 * <p>Provides endpoints for setting and getting family name, and generating
 * advice on how given names flow with the family name.</p>
 *
 * <p>Per ADR 0002, the advice gracefully degrades when the LLM is unavailable
 * - the UI shows a friendly busy message, but other features remain functional.</p>
 */
@Controller
@RequestMapping("/advice")
public class FullNameAdviceController {

    private final FullNameAdviceService adviceService;

    public FullNameAdviceController(FullNameAdviceService adviceService) {
        this.adviceService = adviceService;
    }

    /**
     * Show the advice page with the current family name (if set) and shortlist.
     *
     * @param model the Thymeleaf model
     * @return view name for the advice page
     */
    @GetMapping
    public String advicePage(Model model) {
        // Get the current user's family name
        Optional<String> familyNameOpt = adviceService.getCurrentUserFamilyName();
        model.addAttribute("familyName", familyNameOpt.orElse(""));
        model.addAttribute("hasFamilyName", familyNameOpt.isPresent());

        // Get the user's shortlist entries for given names
        List<com.baibyname.domain.ShortlistEntry> entries = adviceService.getCurrentUserEntries();
        model.addAttribute("entries", entries);
        model.addAttribute("givenNames", entries.stream()
                .map(e -> e.getGivenName().getName())
                .toList());
        model.addAttribute("hasEntries", !entries.isEmpty());

        return "advice";
    }

    /**
     * Set the family name for the current authenticated user.
     *
     * @param familyName the family name to set
     * @return redirect to advice page
     */
    @PostMapping("/family-name")
    public String setFamilyName(@RequestParam String familyName) {
        adviceService.setCurrentUserFamilyName(familyName);
        return "redirect:/advice";
    }

    /**
     * Generate advice for the given full name combination.
     *
     * @param familyName the family name
     * @param givenNames the given names (comma-separated)
     * @param countries the countries (comma-separated)
     * @param language the UI language
     * @param model the model for error response
     * @return JSON response with advice or error message
     */
    @PostMapping(value = "/generate", produces = "application/json")
    @ResponseBody
    public AdviceResponse generateAdvice(@RequestParam String familyName,
                                         @RequestParam String givenNames,
                                         @RequestParam String countries,
                                         @RequestParam(defaultValue = "en") String language) {
        // Parse given names and countries
        List<String> givenNameList = parseList(givenNames);
        List<String> countriesList = parseList(countries);

        // Validate inputs
        if (givenNameList.isEmpty()) {
            return new AdviceResponse(null, "Please select at least one given name from your shortlist.");
        }
        if (givenNameList.size() > 3) {
            return new AdviceResponse(null, "You can select up to 3 given names for advice.");
        }
        if (familyName == null || familyName.trim().isEmpty()) {
            return new AdviceResponse(null, "Please enter a family name.");
        }
        if (countriesList.isEmpty()) {
            return new AdviceResponse(null, "Please select at least one country.");
        }

        // Generate advice
        String advice = adviceService.generateAdvice(familyName, givenNameList, countriesList, language);

        return new AdviceResponse(advice, null);
    }

    /**
     * Parse a comma-separated string into a list of trimmed strings.
     *
     * @param input the input string
     * @return list of strings
     */
    private List<String> parseList(String input) {
        if (input == null || input.trim().isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Response object for advice generation.
     *
     * @param advice the advice text (may be null if there's an error)
     * @param error the error message (may be null if advice is successful)
     */
    public record AdviceResponse(String advice, String error) {
    }
}
