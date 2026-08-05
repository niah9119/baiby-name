package com.baibyname.exception;

/**
 * Exception thrown when a share token is not found.
 * This is used for shared shortlist views to return 404 for invalid tokens.
 */
public class TokenNotFoundException extends RuntimeException {

    public TokenNotFoundException(String token) {
        super("Share token not found: " + token);
    }
}
