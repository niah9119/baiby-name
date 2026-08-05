# Issue #65: Sex Filter Share Threshold Implementation Plan

## Status: In Progress

### Completed:

### In Progress:
- Create share threshold constant
- Add repository method for share-based sex filter
- Add service method for share-based sex filter
- Update BrowseService.getCandidates

### Remaining:
- Write tests for share-based sex filter
- Run tests and verify

## Infrastructure

## Implementation

### Phase 1: Share Threshold Constant
- [ ] Create SHARE_THRESHOLD constant (10%) in BrowseService
- [ ] Document trade-off: per-country vs global computation

### Phase 2: Repository Layer
- [ ] Add method to GivenNameRepository for share-based sex filter
- [ ] Query: find names where specified sex has >= 10% of total count per country

### Phase 3: Service Layer
- [ ] Add method to GivenNameService that wraps the repository method
- [ ] Handle multiple selected sexes (union semantics)

### Phase 4: BrowseService Updates
- [ ] Apply sex filter even when no countries selected
- [ ] Use all selected sexes (union), not just first one
- [ ] Remove iterator().next() pattern

### Phase 5: Tests
- [ ] Test Kim: 20% boys -> appears under both Boy and Girl
- [ ] Test Walter: 0.6% girls -> appears under Boy only
- [ ] Test Alice: 0.3% boys -> appears under Girl only
- [ ] Test Folke: 100% boys -> Boy only
- [ ] Test no-country sex filter
- [ ] Test multiple sex selection (union)

## Verification
