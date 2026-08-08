package com.baibyname.service;

import com.baibyname.domain.Shortlist;
import com.baibyname.domain.ShareLink;
import com.baibyname.repository.ShareLinkRepository;
import com.baibyname.repository.ShortlistRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.OffsetDateTime;
import java.util.Optional;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Service for sharing shortlists via email claiming and unguessable tokens.
 *
 * <p>Implements ADR 0004: allows anonymous visitors to claim their shortlist
 * by providing email and display name, then share it via a read-only link.</p>
 *
 * <p>Two-token deletion: when a shortlist is claimed, two tokens are generated:
 * the share token (for reading) and the owner token (for deletion).
 * The owner token is returned to the claimer and stored securely; recipients
 * who only have the share token cannot delete the list.</p>
 */
@Service
@Validated
public class ShareLinkService {

    private static final int TOKEN_BITS = 128;  // At least 128 bits of entropy
    private static final int TOKEN_BYTES = TOKEN_BITS / 8;

    private final ShareLinkRepository shareLinkRepository;
    private final ShortlistRepository shortlistRepository;
    private final ShortlistService shortlistService;

    public ShareLinkService(ShareLinkRepository shareLinkRepository,
                            ShortlistRepository shortlistRepository,
                            ShortlistService shortlistService) {
        this.shareLinkRepository = shareLinkRepository;
        this.shortlistRepository = shortlistRepository;
        this.shortlistService = shortlistService;
    }

    /**
     * Generate a URL-safe, random token with at least 128 bits of entropy.
     *
     * @return a URL-safe base64-encoded string
     */
    private String generateShareToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        // SecureRandom is thread-safe and suitable for this use
        java.security.SecureRandom random = new java.security.SecureRandom();
        random.nextBytes(randomBytes);
        return Base64UrlEncoder.encode(randomBytes);
    }

    /**
     * Generate a URL-safe, random token with at least 128 bits of entropy for owner operations.
     * Same as share token but generated independently so the owner token is unguessable.
     *
     * @return a URL-safe base64-encoded string
     */
    private String generateOwnerToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        // SecureRandom is thread-safe and suitable for this use
        java.security.SecureRandom random = new java.security.SecureRandom();
        random.nextBytes(randomBytes);
        return Base64UrlEncoder.encode(randomBytes);
    }

    /**
     * Claim a shortlist by providing email and display name.
     * Creates a share link with two tokens (share token and owner token) and returns the owner token.
     *
     * <p>The share token is stored in the database and used for read-only access via the share link.
     * The owner token is returned to the claimer and is required for deletion.
     * Recipients who only have the share token cannot delete the list.</p>
     *
     * @param email the claimer's email address (validated for format)
     * @param displayName the display name for the shortlist
     * @return the owner token if successful (empty if no shortlist exists)
     * @throws IllegalArgumentException if email format is invalid
     */
    @Transactional
    public Optional<String> claimShortlist(@Email String email,
                                           @NotBlank String displayName) {
        // Validate email format using a simple pattern
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email address format");
        }

        // Get the current user's session shortlist (anonymous or authenticated)
        Optional<Shortlist> shortlistOpt = shortlistService.getCurrentUserShortlist();
        if (shortlistOpt.isEmpty()) {
            return Optional.empty();
        }

        Shortlist shortlist = shortlistOpt.get();

        // Check if this shortlist is already claimed
        Optional<ShareLink> existingLinkOpt = shareLinkRepository.findByShortlistId(shortlist.getId());
        if (existingLinkOpt.isPresent()) {
            // For now, we don't allow re-claiming an already-claimed shortlist
            // The existing link remains valid and should be shown to the user
            return Optional.of(existingLinkOpt.get().getOwnerToken());
        }

        // Generate both tokens
        String shareToken = generateShareToken();
        String ownerToken = generateOwnerToken();

        // Create the share link with both tokens
        ShareLink shareLink = new ShareLink();
        shareLink.setShortlist(shortlist);
        shareLink.setEmail(email);
        shareLink.setDisplayName(displayName);
        shareLink.setShareToken(shareToken);
        shareLink.setOwnerToken(ownerToken);
        shareLink.setAccessLevel(ShareLink.AccessLevel.READ_ONLY);
        shareLink.setCreatedAt(OffsetDateTime.now());

        shareLinkRepository.save(shareLink);

        // Return the owner token (not the share token) - only the owner should know this
        return Optional.of(ownerToken);
    }

    /**
     * Get the share link for a given token.
     *
     * @param token the share token
     * @return the share link if found, empty otherwise
     */
    public Optional<ShareLink> findByToken(String token) {
        return shareLinkRepository.findByShareToken(token);
    }

    /**
     * Get the shortlist for a given token (with entries fetched).
     *
     * @param token the share token
     * @return the shortlist with entries if the token is valid, empty otherwise
     */
    public Optional<Shortlist> getShortlistByToken(String token) {
        return shareLinkRepository.findByShareToken(token)
                .map(ShareLink::getShortlist);
    }

    /**
     * Get entries for a shared shortlist by token.
     *
     * @param token the share token
     * @return list of entries for the shortlist, empty if token invalid
     */
    public java.util.List<com.baibyname.domain.ShortlistEntry> getEntriesByToken(String token) {
        return shareLinkRepository.findByShareToken(token)
                .map(link -> shortlistService.getEntriesById(link.getShortlist().getId()))
                .orElse(java.util.List.of());
    }

    /**
     * Delete a share link by token.
     * Accepts either the share token (read-only access) or the owner token (deletion).
     *
     * <p>Only the owner token grants deletion permission. The share token can view
     * the shortlist but cannot delete it.</p>
     *
     * @param token the owner token for deletion (or share token which will be rejected)
     * @return true if deleted, false if not found or wrong token type
     */
    @Transactional
    public boolean deleteByToken(String token) {
        // First try to find by owner token with the shortlist attached
        Optional<ShareLink> linkOpt = shareLinkRepository.findByOwnerTokenWithShortlist(token);
        if (linkOpt.isEmpty()) {
            // If not found by owner token, try share token
            // If found by share token, deletion is not allowed
            linkOpt = shareLinkRepository.findByShareToken(token);
            if (linkOpt.isPresent()) {
                // Token exists but is a share token, not an owner token
                // This means a recipient is trying to delete - reject
                return false;
            }
            // Neither token found
            return false;
        }

        ShareLink link = linkOpt.get();
        Shortlist shortlist = link.getShortlist();
        Long shortlistId = shortlist.getId();

        // Delete the share link by ID first (before deleting the shortlist)
        // This avoids transient object issues when the shortlist is deleted
        shareLinkRepository.deleteById(link.getId());

        // Delete all entries in the shortlist
        shortlistService.deleteShortlistById(shortlistId);

        return true;
    }

    /**
     * Check if the given token is a valid owner token.
     *
     * @param token the token to verify
     * @return true if the token is a valid owner token, false otherwise
     */
    public boolean isOwnerToken(String token) {
        // Try to find by owner token - if found, it's a valid owner token
        return shareLinkRepository.findByOwnerToken(token).isPresent();
    }

    /**
     * Basic email format validation using a regex pattern.
     * This is a simple check - for production use, consider a more robust validator.
     *
     * @param email the email to validate
     * @return true if the email matches a basic pattern
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // Basic RFC 5322 compliant pattern (simplified for practical use)
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    /**
     * URL-safe Base64 encoder (no padding).
     */
    private static class Base64UrlEncoder {
        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

        static String encode(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes.length; i += 3) {
                int b1 = bytes[i] & 0xFF;
                int b2 = (i + 1 < bytes.length) ? bytes[i + 1] & 0xFF : 0;
                int b3 = (i + 2 < bytes.length) ? bytes[i + 2] & 0xFF : 0;

                sb.append(ALPHABET.charAt(b1 >> 2));
                sb.append(ALPHABET.charAt(((b1 & 0x3) << 4) | (b2 >> 4)));
                if (i + 1 < bytes.length) {
                    sb.append(ALPHABET.charAt(((b2 & 0xF) << 2) | (b3 >> 6)));
                }
                if (i + 2 < bytes.length) {
                    sb.append(ALPHABET.charAt(b3 & 0x3F));
                }
            }
            return sb.toString();
        }
    }
}
