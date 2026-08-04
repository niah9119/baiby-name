package com.baibyname.service;

import com.baibyname.domain.Account;
import com.baibyname.domain.FamilyName;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.Shortlist;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.domain.ShortlistMember;
import com.baibyname.llm.ChatCompletionRequest;
import com.baibyname.llm.ChatCompletionResponse;
import com.baibyname.llm.ChatMessage;
import com.baibyname.llm.LlmGateway;
import com.baibyname.repository.AccountRepository;
import com.baibyname.repository.FamilyNameRepository;
import com.baibyname.repository.GivenNameRepository;
import com.baibyname.repository.ShortlistEntryRepository;
import com.baibyname.repository.ShortlistMemberRepository;
import com.baibyname.repository.ShortlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for FullNameAdviceService.
 */
class FullNameAdviceServiceTest {

    private LlmGateway llmGateway;
    private AccountRepository accountRepository;
    private FamilyNameRepository familyNameRepository;
    private GivenNameRepository givenNameRepository;
    private ShortlistRepository shortlistRepository;
    private ShortlistEntryRepository shortlistEntryRepository;
    private ShortlistMemberRepository shortlistMemberRepository;
    private MessageSource messageSource;
    private FullNameAdviceService adviceService;

    @BeforeEach
    void setUp() {
        llmGateway = mock(LlmGateway.class);
        accountRepository = mock(AccountRepository.class);
        familyNameRepository = mock(FamilyNameRepository.class);
        givenNameRepository = mock(GivenNameRepository.class);
        shortlistRepository = mock(ShortlistRepository.class);
        shortlistEntryRepository = mock(ShortlistEntryRepository.class);
        shortlistMemberRepository = mock(ShortlistMemberRepository.class);
        messageSource = mock(MessageSource.class);
        // Stub messageSource to return message containing the key for tests
        // For Swedish, return a message containing "inte tillgänglig"; for others, "unavailable"
        when(messageSource.getMessage(eq("advice.unavailable"), any(), any()))
                .thenAnswer(invocation -> {
                    Locale locale = invocation.getArgument(2, Locale.class);
                    if ("sv".equals(locale.getLanguage())) {
                        return "Tjänsten är inte tillgänglig";
                    }
                    return "The service is unavailable";
                });
        adviceService = new FullNameAdviceService(
                llmGateway, accountRepository, familyNameRepository,
                givenNameRepository, shortlistRepository, shortlistEntryRepository,
                shortlistMemberRepository, messageSource);
    }

    private void mockAuthenticatedUser() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
    }

    private void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserFamilyName_returnsFamilyName_whenAuthenticated() {
        // Given
        mockAuthenticatedUser();
        Account account = new Account();
        account.setId(1L);
        account.setEmail("test@example.com");

        FamilyName familyName = new FamilyName();
        familyName.setName("Smith");
        account.setFamilyName(familyName);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));

        // When
        Optional<String> result = adviceService.getCurrentUserFamilyName();

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("Smith");
    }

    @Test
    void getCurrentUserFamilyName_returnsEmpty_whenNoFamilyName() {
        // Given
        mockAuthenticatedUser();
        Account account = new Account();
        account.setId(1L);
        account.setEmail("test@example.com");
        account.setFamilyName(null);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));

        // When
        Optional<String> result = adviceService.getCurrentUserFamilyName();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getCurrentUserFamilyName_returnsEmpty_whenNotAuthenticated() {
        // Given
        clearAuthentication();

        // When
        Optional<String> result = adviceService.getCurrentUserFamilyName();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void setCurrentUserFamilyName_createsNew_whenNoneExists() {
        // Given
        mockAuthenticatedUser();
        Account account = new Account();
        account.setId(1L);
        account.setEmail("test@example.com");

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(familyNameRepository.findByAccountId(1L)).thenReturn(Optional.empty());
        when(familyNameRepository.save(any(FamilyName.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        boolean result = adviceService.setCurrentUserFamilyName("Smith");

        // Then
        assertThat(result).isTrue();
        verify(familyNameRepository).findByAccountId(1L);
        verify(familyNameRepository).save(any(FamilyName.class));
    }

    @Test
    void setCurrentUserFamilyName_updatesExisting_whenExists() {
        // Given
        mockAuthenticatedUser();
        Account account = new Account();
        account.setId(1L);
        account.setEmail("test@example.com");

        FamilyName existingFamilyName = new FamilyName();
        existingFamilyName.setId(1L);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(familyNameRepository.findByAccountId(1L)).thenReturn(Optional.of(existingFamilyName));
        when(familyNameRepository.save(any(FamilyName.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        boolean result = adviceService.setCurrentUserFamilyName("Johnson");

        // Then
        assertThat(result).isTrue();
        assertThat(existingFamilyName.getName()).isEqualTo("Johnson");
        verify(familyNameRepository).save(existingFamilyName);
    }

    @Test
    void setCurrentUserFamilyName_returnsFalse_whenNotAuthenticated() {
        // Given
        clearAuthentication();

        // When
        boolean result = adviceService.setCurrentUserFamilyName("Smith");

        // Then
        assertThat(result).isFalse();
        verifyNoInteractions(familyNameRepository);
    }

    @Test
    void deleteFamilyNameByAccountId_deletesWhenExists() {
        // Given
        FamilyName familyName = new FamilyName();
        familyName.setId(1L);

        when(familyNameRepository.findByAccountId(1L)).thenReturn(Optional.of(familyName));

        // When
        boolean result = adviceService.deleteFamilyNameByAccountId(1L);

        // Then
        assertThat(result).isTrue();
        verify(familyNameRepository).delete(familyName);
    }

    @Test
    void deleteFamilyNameByAccountId_returnsTrue_whenNoneExists() {
        // Given
        when(familyNameRepository.findByAccountId(1L)).thenReturn(Optional.empty());

        // When
        boolean result = adviceService.deleteFamilyNameByAccountId(1L);

        // Then
        assertThat(result).isTrue();
        // The method returns early without calling delete when family name doesn't exist
        // But we still call findByAccountId, so verify that interaction
        verify(familyNameRepository).findByAccountId(1L);
        // And verify delete was NOT called
        verifyNoMoreInteractions(familyNameRepository);
    }

    @Test
    void generateAdvice_returnsAdvice_whenLlmAvailable() throws Exception {
        // Given
        mockAuthenticatedUser();
        when(llmGateway.isAvailable()).thenReturn(true);

        ChatCompletionResponse response = new ChatCompletionResponse();
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        ChatMessage message = new ChatMessage(ChatMessage.Role.ASSISTANT, "The names flow well together.");
        choice.setMessage(message);
        response.setChoices(List.of(choice));

        when(llmGateway.chatCompletion(any(ChatCompletionRequest.class))).thenReturn(response);

        // When
        String result = adviceService.generateAdvice("Smith", List.of("John", "Michael"), List.of("US"), "en");

        // Then
        assertThat(result).contains("flow well");
        verify(llmGateway).chatCompletion(any(ChatCompletionRequest.class));
    }

    @Test
    void generateAdvice_returnsUnavailableMessage_whenLlmDown() throws Exception {
        // Given
        when(llmGateway.isAvailable()).thenReturn(false);

        // When
        String result = adviceService.generateAdvice("Smith", List.of("John"), List.of("US"), "en");

        // Then
        assertThat(result).contains("unavailable");
        // Verify that isAvailable was called
        verify(llmGateway).isAvailable();
    }

    @Test
    void generateAdvice_returnsUnavailableMessage_whenLlmThrowsException() throws Exception {
        // Given
        when(llmGateway.isAvailable()).thenReturn(true);
        when(llmGateway.chatCompletion(any(ChatCompletionRequest.class)))
                .thenThrow(new LlmGateway.LlmUnavailableException("Connection failed"));

        // When
        String result = adviceService.generateAdvice("Smith", List.of("John"), List.of("US"), "en");

        // Then
        assertThat(result).contains("unavailable");
    }

    @Test
    void generateAdvice_returnsSwedishMessage_whenSwedishAndLlmDown() throws Exception {
        // Given
        when(llmGateway.isAvailable()).thenReturn(false);

        // When
        String result = adviceService.generateAdvice("Svensson", List.of("Elsa"), List.of("SE"), "sv");

        // Then
        // The Swedish unavailable message contains "inte tillgänglig"
        assertThat(result).contains("inte tillgänglig");
    }

    @Test
    void getCurrentUserEntries_returnsEntries_whenAuthenticated() {
        // Given
        mockAuthenticatedUser();
        Account account = new Account();
        account.setId(1L);

        Shortlist shortlist = new Shortlist();
        shortlist.setId(1L);

        ShortlistMember member = new ShortlistMember();
        member.setShortlist(shortlist);
        member.setAccount(account);

        GivenName givenName1 = new GivenName();
        givenName1.setId(1L);
        givenName1.setName("Elsa");

        GivenName givenName2 = new GivenName();
        givenName2.setId(2L);
        givenName2.setName("Marie");

        ShortlistEntry entry1 = new ShortlistEntry();
        entry1.setGivenName(givenName1);

        ShortlistEntry entry2 = new ShortlistEntry();
        entry2.setGivenName(givenName2);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(shortlistMemberRepository.findMembersByAccount(account)).thenReturn(List.of(member));
        when(shortlistEntryRepository.findEntriesByShortlist(shortlist)).thenReturn(List.of(entry1, entry2));

        // When
        List<ShortlistEntry> result = adviceService.getCurrentUserEntries();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getGivenName().getName()).isEqualTo("Elsa");
        assertThat(result.get(1).getGivenName().getName()).isEqualTo("Marie");
    }

    @Test
    void getCurrentUserEntries_returnsEmpty_whenNotAuthenticated() {
        // Given
        clearAuthentication();

        // When
        List<ShortlistEntry> result = adviceService.getCurrentUserEntries();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getCurrentUserEntries_returnsEmpty_whenNoShortlist() {
        // Given
        mockAuthenticatedUser();
        Account account = new Account();
        account.setId(1L);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(shortlistMemberRepository.findMembersByAccount(account)).thenReturn(List.of());

        // When
        List<ShortlistEntry> result = adviceService.getCurrentUserEntries();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void generateAdvice_stripsHallucinatedNames_whenLLMsuggestsUnknownName() throws Exception {
        // Given - selected names are John and Michael, but LLM suggests Astrid (a known name in DB)
        mockAuthenticatedUser();
        when(llmGateway.isAvailable()).thenReturn(true);

        // Mock the database to contain "Astrid" as a known name
        GivenName astrid = new GivenName();
        astrid.setId(1L);
        astrid.setName("Astrid");
        when(givenNameRepository.findByNameIn(any(List.class))).thenReturn(List.of(astrid));

        // The LLM returns advice that includes Astrid (a hallucinated name)
        ChatCompletionResponse response = new ChatCompletionResponse();
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        ChatMessage message = new ChatMessage(ChatMessage.Role.ASSISTANT,
                "The names John and Michael flow well together. You might also consider Astrid for a girl's name.");
        choice.setMessage(message);
        response.setChoices(List.of(choice));

        when(llmGateway.chatCompletion(any(ChatCompletionRequest.class))).thenReturn(response);

        // When
        String result = adviceService.generateAdvice("Smith", List.of("John", "Michael"), List.of("US"), "en");

        // Then - Astrid should be stripped and replaced with [name redacted]
        assertThat(result).doesNotContain("Astrid");
        assertThat(result).contains("John and Michael flow well together.");
        assertThat(result).contains("[name redacted] for a girl's name.");
        verify(givenNameRepository).findByNameIn(any(List.class));
    }

    @Test
    void generateAdvice_returnsAdviceUnchanged_whenLLMdoesNothallucinate() throws Exception {
        // Given - selected names are John and Michael, no known names in DB that appear in advice
        mockAuthenticatedUser();
        when(llmGateway.isAvailable()).thenReturn(true);

        // Mock the database - Olivia is in DB but doesn't appear in advice (and John/Michael are selected)
        GivenName otherName = new GivenName();
        otherName.setId(1L);
        otherName.setName("Olivia");
        when(givenNameRepository.findByNameIn(any(List.class))).thenReturn(List.of(otherName));

        // The LLM returns advice that doesn't mention any names from the DB (except the selected ones)
        ChatCompletionResponse response = new ChatCompletionResponse();
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        ChatMessage message = new ChatMessage(ChatMessage.Role.ASSISTANT,
                "The names John and Michael flow well together with Smith.");
        choice.setMessage(message);
        response.setChoices(List.of(choice));

        when(llmGateway.chatCompletion(any(ChatCompletionRequest.class))).thenReturn(response);

        // When
        String result = adviceService.generateAdvice("Smith", List.of("John", "Michael"), List.of("US"), "en");

        // Then - advice should be unchanged (Olivia is not mentioned, John/Michael are selected)
        assertThat(result).contains("John and Michael flow well together with Smith.");
        verify(givenNameRepository).findByNameIn(any(List.class));
    }

    @Test
    void generateAdvice_usesBoundedQuery_withRealisticNameCount() throws Exception {
        // Given - create a realistic number of names (a few thousand)
        mockAuthenticatedUser();
        when(llmGateway.isAvailable()).thenReturn(true);

        // Create 3000 names to simulate a realistic database
        int nameCount = 3000;
        List<GivenName> allNames = new ArrayList<>();
        for (int i = 0; i < nameCount; i++) {
            GivenName name = new GivenName();
            name.setId((long) i);
            name.setName("Name" + i);
            allNames.add(name);
        }

        // The advice mentions only "Name100" and "Name2000" (both in DB)
        // but not all 3000 names - the bounded query should only look for candidates
        when(givenNameRepository.findByNameIn(any(List.class)))
                .thenAnswer(invocation -> {
                    // Verify that only a small number of candidates are queried (bounded)
                    List<String> candidates = invocation.getArgument(0);
                    // With tokenization we expect ~5-10 candidates, not 3000
                    assertThat(candidates.size()).isLessThan(100);
                    // Verify the candidates are actual name-like words from the advice
                    assertThat(candidates).contains("Name100");
                    assertThat(candidates).contains("Name2000");
                    // Filter to only those that actually exist in the DB
                    return allNames.stream()
                            .filter(n -> candidates.contains(n.getName()))
                            .toList();
                });

        // The LLM returns advice mentioning specific names from the DB
        ChatCompletionResponse response = new ChatCompletionResponse();
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        ChatMessage message = new ChatMessage(ChatMessage.Role.ASSISTANT,
                "The names John and Name100 flow well together. Name2000 is also a good choice.");
        choice.setMessage(message);
        response.setChoices(List.of(choice));

        when(llmGateway.chatCompletion(any(ChatCompletionRequest.class))).thenReturn(response);

        // When
        String result = adviceService.generateAdvice("Smith", List.of("John"), List.of("US"), "en");

        // Then - Name100 and Name2000 are not in selected names (only John), so they are hallucinations
        // and should be stripped. The bounded query was used (verified in the mock answer).
        assertThat(result).contains("[name redacted]");
        // Verify the bounded query was used
        verify(givenNameRepository).findByNameIn(any(List.class));
    }
}
