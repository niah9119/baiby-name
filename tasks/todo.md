# Full-Name Advice Implementation Plan

## Status: In Progress

### Completed:
- Family name storage schema (V4 migration)
- FamilyName entity with account relationship
- FamilyNameRepository with findByAccountId
- AccountService updated to delete family name on account deletion
- All tests passing

### In Progress:
- LLM tool definition for full-name advice
- FullNameAdviceService with LLM integration
- FullNameAdviceController
- Tests for advice functionality

### Remaining:

### Blocked:

## Infrastructure

## Implementation

### Phase 1: Database Schema
- [x] Create V4__family_name.sql migration
- [x] Create FamilyName entity

### Phase 2: Repository Layer
- [x] Create FamilyNameRepository

### Phase 3: Service Layer
- [x] Create FamilyNameService with CRUD and GDPR erasure
- [ ] Create FullNameAdviceService with LLM integration

### Phase 4: Controller Layer
- [ ] Create FullNameAdviceController

### Phase 5: Tests
- [ ] Write repository tests
- [ ] Write service tests with stubbed LLM
- [ ] Write integration tests

## Verification

## PR
