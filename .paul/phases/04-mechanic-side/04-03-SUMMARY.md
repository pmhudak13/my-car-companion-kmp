---
phase: 04-mechanic-side
plan: 03
status: complete
---

# Summary: Admin Mechanic Verification UI

## What was done

### Task 1: ProfileRepository.kt
Added three new suspend functions after `getMyMechanicProfile()`:
- `getAllMechanicProfiles()` — queries `mechanic_profiles` ordered by `created_at DESC`, returns `Result<List<MechanicProfile>>`
- `approveMechanic(userId)` — sets `verification_status="verified"` and `verified_at=now()`, then inserts `mechanic` role into `user_roles` (failure silently ignored so duplicate roles don't break flow)
- `rejectMechanic(userId)` — sets `verification_status="rejected"`

### Task 2: AdminScreenModel.kt
- Added four new fields to `AdminUiState`: `mechanics`, `mechanicsLoading`, `mechanicsError`, `processingMechanicId`
- `init` now calls both `loadUsers()` and `loadMechanics()`
- Added `loadMechanics()`, `approveMechanic(userId)`, `rejectMechanic(userId)` — all update state optimistically on success
- No existing methods changed

### Task 3: AdminScreen.kt
- Restructured `Scaffold` content into a `Column` with a header row, `TabRow` (Users / Mechanics), and conditional tab content
- Tab 0 (Users): existing list content, identical behavior
- Tab 1 (Mechanics): `LazyColumn` of `MechanicAdminCard` items
- Added `MechanicAdminCard` private composable: shows shop name, `VerificationBadge`, type, location, exp/rate, and Approve/Reject buttons (or spinner while processing) for pending mechanics
- Added `VerificationBadge` private composable: outlined badge colored by status (pending=outline, verified=primary, rejected=error)
- All existing composables (`UserCard`, `RoleBadge`, `PremiumBadge`) unchanged

## Acceptance criteria
- AC-1: Admin sees all mechanic profiles in Mechanics tab
- AC-2: Approve sets verified status + mechanic role in user_roles
- AC-3: Reject sets rejected status; UI card updates immediately
