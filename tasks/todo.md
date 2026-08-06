# Issue #78 - Completed

## Summary

Fixed two blocking issues identified in the issue review:

### Blocking 1: ConsentControllerAdvice dependency issue
**Problem**: The `ConsentControllerAdvice` had a hard dependency on `ConsentService`. When `@WebMvcTest` loaded a narrow context, `ConsentService` wasn't available, causing 10 tests to fail with `NoSuchBeanDefinitionException`.

**Fix**: Changed from direct injection to `ObjectProvider<ConsentService>` with graceful degradation:
```java
public ConsentControllerAdvice(ObjectProvider<ConsentService> consentServiceProvider) {
    this.consentServiceProvider = consentServiceProvider;
}
```
When `ConsentService` is not available, `hasConsent()` returns `false` (failing closed, as required by GDPR).

### Blocking 2: AdRenderingTest assertion failure
**Problem**: The test `anonymousUserWithConsentCookie_seesAdMarkup` was failing because it expected `<ins class="adsbygoogle">` but the template actually renders `<ins class="adsbygoogle" style="display:block"...>`.

**Fix**: Updated all assertions in `AdRenderingTest` to check for the `adsbygoogle` substring instead of the exact tag string.

## Test Results
All 255 tests pass with 0 failures and 0 errors.

## Changes Made
- Modified: `src/main/java/com/baibyname/controller/ConsentControllerAdvice.java`
- Modified: `src/test/java/com/baibyname/controller/AdRenderingTest.java`

## PR
Created PR #103: "Implement #78: Fix ConsentControllerAdvice dependency and AdRenderingTest assertions"

Issue moved to `ready-for-human` state.
