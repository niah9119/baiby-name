# Issue #78 - Consent Gate Rendering Tests and Anonymous Consent

## Status
In progress

## Tasks

### 1. Create rendering tests for ad markup
- [ ] Test consenting logged-in visitor sees ad markup
- [ ] Test logged-in visitor who declined consent
- [ ] Test anonymous visitor (no consent)
- [ ] Verify empty states are distinct (consent required vs slot not configured)

### 2. Fix anonymous visitor ad consent mechanism
- [x] Add method to ConsentService for checking consent from cookie
- [x] Update layout template to set cookie via JavaScript
- [x] Update AdService to check consent cookie for anonymous users
- [ ] Test anonymous visitor with valid consent signal

## Implementation Notes
- Consent cookie will be set by JavaScript when user accepts/declines consent
- For anonymous users, the cookie contains: `{"cookies": true, "processing": true, "marketing": true}`
- The cookie will be readable server-side and checked by AdService
- Default (no cookie) = no consent (fails closed, as per GDPR)
