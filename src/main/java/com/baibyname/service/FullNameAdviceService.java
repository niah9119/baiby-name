package com.baibyname.service;

import com.baibyname.domain.Account;
import com.baibyname.domain.FamilyName;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.llm.*;
import com.baibyname.repository.AccountRepository;
import com.baibyname.repository.FamilyNameRepository;
import com.baibyname.repository.ShortlistEntryRepository;
import com.baibyname.repository.ShortlistMemberRepository;
import com.baibyname.repository.ShortlistRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * alternative names or generates new data.</p>
 */
@Service
public class FullNameAdviceService {

    private final LlmGateway llmGateway;
    private final AccountRepository accountRepository;
    private final FamilyNameRepository familyNameRepository;
    private final ShortlistRepository shortlistRepository;
    private final ShortlistEntryRepository shortlistEntryRepository;
    private final ShortlistMemberRepository shortlistMemberRepository;

    public FullNameAdviceService(LlmGateway llmGateway,
                                  AccountRepository accountRepository,
                                  FamilyNameRepository familyNameRepository,
                                  ShortlistRepository shortlistRepository,
                                  ShortlistEntryRepository shortlistEntryRepository,
                                  ShortlistMemberRepository shortlistMemberRepository) {
        this.llmGateway = llmGateway;
        this.accountRepository = accountRepository;
        this.familyNameRepository = familyNameRepository;
        this.shortlistRepository = shortlistRepository;
        this.shortlistEntryRepository = shortlistEntryRepository;
        this.shortlistMemberRepository = shortlistMemberRepository;
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
            if ("sv".equals(language)) {
                return "Tjänsten är för tillfället inte tillgänglig. Försök igen senare.";
            } else {
                return "The service is currently unavailable. Please try again later.";
            }
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
                return response.getChoices().get(0).getMessage().getContent();
            }
            return getUnavailableMessage(language);
        } catch (LlmGateway.LlmUnavailableException e) {
            return getUnavailableMessage(language);
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
     * Get the unavailable message for the specified language.
     *
     * @param language the language code
     * @return the unavailable message
     */
    private String getUnavailableMessage(String language) {
        if ("sv".equals(language)) {
            return "Tjänsten är för tillfället inte tillgänglig. Försök igen senare.";
        } else {
            return "The service is currently unavailable. Please try again later.";
        }
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
