package com.baibyname.service;

import com.baibyname.domain.Account;
import com.baibyname.domain.FamilyName;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.llm.*;
import com.baibyname.repository.AccountRepository;
import com.baibyname.repository.FamilyNameRepository;
import com.baibyname.repository.GivenNameRepository;
import com.baibyname.repository.ShortlistEntryRepository;
import com.baibyname.repository.ShortlistMemberRepository;
import com.baibyname.repository.ShortlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Service for generating full-name advice.
 *
 * <p>This service provides advice on how one or more given names flow together
 * with the family name when spoken as a whole (e.g., "Elsa Marie Ahlstrand").</p>
 *
 * <p>Per ADR 0002, if the LLM is unavailable, the service gracefully degrades
 * to a friendly busy message.</p>
 *
 * <p>Per ADR 0001, the LLM only provides prose advice - it never suggests
 * alternative names or generates new data. A code-side validation checks the
 * advice for hallucinated names and strips them if found.</p>
 */
@Service
public class FullNameAdviceService {

    private static final Logger logger = LoggerFactory.getLogger(FullNameAdviceService.class);

    private final LlmGateway llmGateway;
    private final AccountRepository accountRepository;
    private final FamilyNameRepository familyNameRepository;
    private final GivenNameRepository givenNameRepository;
    private final ShortlistRepository shortlistRepository;
    private final ShortlistEntryRepository shortlistEntryRepository;
    private final ShortlistMemberRepository shortlistMemberRepository;
    private final MessageSource messageSource;

    public FullNameAdviceService(LlmGateway llmGateway,
                                  AccountRepository accountRepository,
                                  FamilyNameRepository familyNameRepository,
                                  GivenNameRepository givenNameRepository,
                                  ShortlistRepository shortlistRepository,
                                  ShortlistEntryRepository shortlistEntryRepository,
                                  ShortlistMemberRepository shortlistMemberRepository,
                                  MessageSource messageSource) {
        this.llmGateway = llmGateway;
        this.accountRepository = accountRepository;
        this.familyNameRepository = familyNameRepository;
        this.givenNameRepository = givenNameRepository;
        this.shortlistRepository = shortlistRepository;
        this.shortlistEntryRepository = shortlistEntryRepository;
        this.shortlistMemberRepository = shortlistMemberRepository;
        this.messageSource = messageSource;
    }

    /**
     * Convert a language code to a Locale.
     *
     * @param languageCode the language code (e.g., "en", "sv")
     * @return the Locale
     */
    private Locale getLocale(String languageCode) {
        if ("sv".equals(languageCode)) {
            return new Locale("sv");
        }
        return Locale.ENGLISH;
    }

    /**
     * Get the family name for the current authenticated user.
     *
     * @return the family name for the current user's account, or empty if not set
     */
    public Optional<String> getCurrentUserFamilyName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        String username = authentication.getName();
        return accountRepository.findByEmail(username)
                .flatMap(account -> Optional.ofNullable(account.getFamilyName()))
                .map(FamilyName::getName);
    }

    /**
     * Set the family name for the current authenticated user.
     *
     * @param familyName the family name to set
     * @return true if set successfully, false if user is not authenticated
     */
    public boolean setCurrentUserFamilyName(String familyName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String username = authentication.getName();
        Optional<Account> accountOpt = accountRepository.findByEmail(username);

        if (accountOpt.isEmpty()) {
            return false;
        }

        Account account = accountOpt.get();
        FamilyName familyNameEntity = familyNameRepository.findByAccountId(account.getId())
                .orElseGet(() -> {
                    FamilyName fn = new FamilyName();
                    fn.setAccount(account);
                    return fn;
                });

        familyNameEntity.setName(familyName);
        familyNameRepository.save(familyNameEntity);
        return true;
    }

    /**
     * Delete the family name for the current authenticated user.
     * Used during account deletion (GDPR right to erasure).
     *
     * @param accountId the account ID
     * @return true if deleted successfully
     */
    public boolean deleteFamilyNameByAccountId(Long accountId) {
        Optional<FamilyName> familyNameOpt = familyNameRepository.findByAccountId(accountId);
        if (familyNameOpt.isEmpty()) {
            return true; // Nothing to delete
        }

        familyNameRepository.delete(familyNameOpt.get());
        return true;
    }

    /**
     * Generate advice for the given full name combination.
     *
     * @param familyName the family name
     * @param givenNames the given names in order (1-3 names)
     * @param countries the countries where the name will be used
     * @param language the UI language for the advice
     * @return the advice text, or an unavailable message if LLM is down
     */
    public String generateAdvice(String familyName, List<String> givenNames, List<String> countries, String language) {
        // Check LLM availability first - per ADR 0002, degrade gracefully
        if (!llmGateway.isAvailable()) {
            return messageSource.getMessage("advice.unavailable", null, getLocale(language));
        }

        // Build the prompt for the LLM
        String prompt = buildAdvicePrompt(familyName, givenNames, countries, language);

        // Build the request
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(prompt));

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .messages(messages)
                .stream(false)
                .build();

        try {
            ChatCompletionResponse response = llmGateway.chatCompletion(request);
            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                String advice = response.getChoices().get(0).getMessage().getContent();
                // Validate that advice doesn't contain hallucinated names - per ADR 0001
                String validatedAdvice = validateAdviceForHallucinatedNames(advice, givenNames, language);
                return validatedAdvice;
            }
            return messageSource.getMessage("advice.unavailable", null, getLocale(language));
        } catch (LlmGateway.LlmUnavailableException e) {
            return messageSource.getMessage("advice.unavailable", null, getLocale(language));
        }
    }

    /**
     * Build the prompt for generating full-name advice.
     *
     * @param familyName the family name
     * @param givenNames the given names in order
     * @param countries the countries where the name will be used
     * @param language the UI language
     * @return the prompt string
     */
    private String buildAdvicePrompt(String familyName, List<String> givenNames, List<String> countries, String language) {
        StringBuilder prompt = new StringBuilder();

        if ("sv".equals(language)) {
            prompt.append("Du är en expert på barnnamn. Din uppgift är att analysera hur ett eller flera förnamn ");
            prompt.append("flödar ihop med efternamnet när de uttalas som en helhet.\n\n");
        } else {
            prompt.append("You are a baby name expert. Your task is to analyze how one or more given names ");
            prompt.append("flow together with the family name when spoken as a whole.\n\n");
        }

        prompt.append("Family name: ").append(familyName).append("\n");
        prompt.append("Given name").append(givenNames.size() == 1 ? "" : "s").append(": ")
                .append(String.join(" ", givenNames)).append("\n");
        if (countries != null && !countries.isEmpty()) {
            prompt.append("Countries: ").append(String.join(", ", countries)).append("\n");
        }
        prompt.append("Language: ").append(language).append("\n\n");

        if ("sv".equals(language)) {
            prompt.append("Tillhandahåll en kort analyse (1-2 stycken) som täcker:\n");
            prompt.append("- Rytm och flöde mellan namnen\n");
            prompt.append("- Eventuella initialproblemer eller olyckliga kombinationer\n");
            prompt.append("- Uttalet i de valda länderna\n\n");
            prompt.append("Viktigt: Ge bara råd om den angivna kombinationen. Föreslå INTE alternativa namn.");
        } else {
            prompt.append("Provide a brief analysis (1-2 paragraphs) covering:\n");
            prompt.append("- Rhythm and flow between the names\n");
            prompt.append("- Any initials pitfalls or unfortunate combinations\n");
            prompt.append("- Pronunciation across the selected countries\n\n");
            prompt.append("CRITICAL: Only provide advice about the specific combination given. ");
            prompt.append("DO NOT suggest alternative names.");
        }

        return prompt.toString();
    }

    /**
     * Validate that advice doesn't contain hallucinated given names.
     *
     * <p>Per ADR 0001, the LLM only provides prose advice - it never suggests
     * alternative names. This method scans the advice for known given names
     * from the database that are NOT in the selected set, and strips them.
     *
     * <p>Approach: We query the database for all known names that appear in
     * the advice text. Any name that is found in the database but not in the
     * selected set is considered a hallucination and is stripped from the
     * advice. The stripping is done by removing the name from the text.
     * This is a simple v1 approach with the following limits:
     * <ul>
     *   <li>Only detects names that exist in the database</li>
     *   <li>Strips the name but keeps surrounding context (could leave awkward phrasing)</li>
     *   <li>Case-sensitive matching for database names</li>
     *   <li>Does not detect misspelled or altered names</li>
     * </ul>
     *
     * @param advice the advice text from the LLM
     * @param givenNames the selected given names
     * @param language the UI language
     * @return the validated advice with hallucinated names stripped, or the original advice if valid
     */
    private String validateAdviceForHallucinatedNames(String advice, List<String> givenNames, String language) {
        // Get the set of selected names for comparison
        Set<String> selectedNames = new HashSet<>(givenNames);

        // Query the database for any names that appear in the advice
        List<String> namesInAdvice = findNamesInAdvice(advice, givenNames);

        // Check for hallucinated names (names in advice that are NOT in selected set)
        for (String name : namesInAdvice) {
            if (!selectedNames.contains(name)) {
                logger.warn("Detected hallucinated name '{}' in advice. Selected names: {}. Stripping from advice.",
                        name, givenNames);
                // Strip the name from advice
                advice = advice.replace(name, "[name redacted]");
            }
        }

        return advice;
    }

    /**
     * Find known given names from the database that appear in the advice text.
     *
     * @param advice the advice text
     * @param givenNames the originally selected names (to limit DB queries)
     * @return list of known names found in advice
     */
    private List<String> findNamesInAdvice(String advice, List<String> givenNames) {
        // Get all names from the database - this is expensive but necessary for validation
        // In production, we could optimize by only checking names that appear in the text
        List<GivenName> allNames = givenNameRepository.findAll();

        List<String> foundNames = new ArrayList<>();
        for (GivenName name : allNames) {
            if (advice.contains(name.getName())) {
                foundNames.add(name.getName());
            }
        }

        return foundNames;
    }

    /**
     * Get the unavailable message for the specified language.
     *
     * @param language the language code
     * @return the unavailable message
     */
    private String getUnavailableMessage(String language) {
        return messageSource.getMessage("advice.unavailable", null, getLocale(language));
    }

    /**
     * Get the shortlist entries for the current authenticated user.
     *
     * @return list of shortlist entries, or empty list if no shortlist exists
     */
    public List<ShortlistEntry> getCurrentUserEntries() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }

        String username = authentication.getName();
        return accountRepository.findByEmail(username)
                .flatMap(account -> {
                    // Find the member for this account, then their shortlist
                    return shortlistMemberRepository.findMembersByAccount(account).stream()
                            .findFirst()
                            .map(member -> member.getShortlist());
                })
                .flatMap(shortlist -> Optional.of(shortlistEntryRepository.findEntriesByShortlist(shortlist)))
                .orElse(List.of());
    }
}
