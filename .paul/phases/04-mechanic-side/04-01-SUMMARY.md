---
phase: 04-mechanic-side
plan: 01
status: complete
---

## Changes Made

### AuthModels.kt
- Added `val isMechanic: Boolean = false` to `AppUser` data class, positioned after `isAdmin`.

### AuthRepository.kt
- Added `val isMechanic = profileRepository.hasRole("mechanic").getOrDefault(false)` alongside the existing admin role check.
- Passed `isMechanic = isMechanic` to the `AppUser` constructor.

### MechanicAssignmentRepository.kt
- Added `import kotlinx.datetime.Clock`.
- Added `getAssignmentsForMechanic(): Result<List<MechanicAssignment>>` — queries `mechanic_assignments` where `mechanic_user_id` == current user and `status == "active"`, ordered by `assigned_at` DESC.
- Added `completeAssignment(id: String): Result<Unit>` — updates `status = "completed"` and `completed_at = Clock.System.now().toString()` for the given assignment ID.

### ProfileRepository.kt
- Added `import kotlinx.datetime.Clock` and `import org.mycarcompanion.app.data.models.MechanicProfile`.
- Added `getMyMechanicProfile(): Result<MechanicProfile?>` — queries `mechanic_profiles` table for the current user's row, returns null if none found.
- Added `upsertMechanicProfile(shopName, shopType, bio, city, state, yearsExperience, hourlyRate): Result<Unit>` — checks for existing profile via `getMyMechanicProfile()`, then updates if exists or inserts if not. Does not touch `verification_status`, `rating`, or `total_jobs`.

## Acceptance Criteria
- AC-1: `AppUser.isMechanic` populated from `user_roles` table via `hasRole("mechanic")`.
- AC-2: `getAssignmentsForMechanic()` returns active assignments for the current mechanic user.
- AC-3: `completeAssignment(id)` sets status and completed_at timestamp.
- AC-4: `getMyMechanicProfile()` and `upsertMechanicProfile()` manage the mechanic's own profile row.
