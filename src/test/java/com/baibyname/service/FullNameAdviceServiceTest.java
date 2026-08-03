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
import com.baibyname.repository.ShortlistEntryRepository;
import com.baibyname.repository.ShortlistMemberRepository;
import com.baibyname.repository.ShortlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
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
    private ShortlistRepository shortlistRepository;
    private ShortlistEntryRepository shortlistEntryRepository;
    private ShortlistMemberRepository shortlistMemberRepository;
    private FullNameAdviceService adviceService;

    @BeforeEach
    void setUp() {
        llmGateway = mock(LlmGateway.class);
        accountRepository = mock(AccountRepository.class);
        familyNameRepository = mock(FamilyNameRepository.class);
        shortlistRepository = mock(ShortlistRepository.class);
        shortlistEntryRepository = mock(ShortlistEntryRepository.class);
        shortlistMemberRepository = mock(ShortlistMemberRepository.class);
        adviceService = new FullNameAdviceService(
                llmGateway, accountRepository, familyNameRepository,
                shortlistRepository, shortlistEntryRepository, shortlistMemberRepository);
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
}
