---
phase: 04-mechanic-side
plan: 02
status: complete
---

## Files Created

- `MechanicSetupScreenModel.kt` — StateFlow-backed ScreenModel with `MechanicSetupState`; init populates form from `getMyMechanicProfile()`; `save()` validates shopName, parses numeric fields, calls `upsertMechanicProfile`, sets `isSaved=true` on success.
- `MechanicSetupScreen.kt` — Scaffold + TopAppBar with back navigation; scrollable form with OutlinedTextFields (shop name, bio, city/state row, years/rate row) and ExposedDropdownMenuBox for shop type; LinearProgressIndicator during profile load; LaunchedEffect on `isSaved` replaces to `MechanicDashboardScreen`.
- `MechanicDashboardScreenModel.kt` — Loads assignments and profile in parallel via `async`/`await`; `completeJob()` sets `completingId`, calls `completeAssignment`, removes item from list; `refresh()` reloads.
- `MechanicDashboardScreen.kt` — Uses both `MechanicDashboardScreenModel` and `HomeScreenModel` (for `authState` + `signOut()`); TopAppBar with Edit icon and Sign Out; LaunchedEffect routes to `LoginScreen` on `Unauthenticated`; `AssignmentCard` composable shows notes, date, per-item `FilledTonalButton`/`CircularProgressIndicator`.

## Files Modified

- `HomeScreen.kt` — `LaunchedEffect(authState)` now uses `when` expression; `Authenticated` branch redirects `isMechanic` users to `MechanicDashboardScreen` via `navigator.replace`.
- `AppModule.kt` — Added `factoryOf(::MechanicDashboardScreenModel)` and `factoryOf(::MechanicSetupScreenModel)`.

## Acceptance Criteria

- AC-1: Mechanic setup form with required shop name, dropdown shop type, and optional fields; navigates to dashboard on save.
- AC-2: Dashboard loads active assignments, shows per-item Complete button with loading state, removes completed items.
- AC-3: Edit icon in dashboard TopAppBar pushes `MechanicSetupScreen` with profile pre-filled from init load.
- AC-4: `HomeScreen` routes `isMechanic == true` users to `MechanicDashboardScreen` instead of car owner view.
