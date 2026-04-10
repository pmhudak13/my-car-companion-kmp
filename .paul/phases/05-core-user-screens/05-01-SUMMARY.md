---
phase: 05-core-user-screens
plan: 01
status: complete
---

# Summary: RemindersListScreen + ProfileScreen

## What was done

### Task 1: Repository Extensions
- `ReminderRepository` — added `getRemindersForVehicles(vehicleIds: List<String>)` using `isIn` filter, ordered by `next_due_date ASC`
- `ProfileRepository` — added `updateProfile(firstName, lastName)` updating the `profiles` table for the current user

### Task 2: RemindersListScreen + RemindersListScreenModel
- `RemindersListScreenModel` — loads vehicles first, then fetches reminders via `getRemindersForVehicles`. Supports filter ("all"/"active"/"overdue") using `kotlinx-datetime` for overdue comparison. Supports delete with confirmation via `deleteConfirmId` state.
- `RemindersListScreen` — TopAppBar + FAB. FAB navigates directly to `AddReminderScreen` if one vehicle, shows a vehicle picker `AlertDialog` if multiple. Filter chips (All/Active/Overdue). `ReminderCard` composable shows type label, vehicle name, due date/mileage, inactive badge. Delete via trash icon → confirmation dialog.

### Task 3: ProfileScreen + ProfileScreenModel
- `ProfileScreenModel` — loads `UserProfile` via `ProfileRepository.getMyProfile()`. Email comes from `UserProfile.email`. `updateFirstName/updateLastName` update state, `save()` calls `ProfileRepository.updateProfile()`.
- `ProfileScreen` — plain `class` (no params). Loading state, error text, two `OutlinedTextField`s for first/last name, read-only email text, Save button with spinner. `LaunchedEffect(state.saved)` pops navigator on success.

### Task 4: AppModule
- Added `factoryOf(::RemindersListScreenModel)` and `factoryOf(::ProfileScreenModel)` to Koin module

## Build result
`BUILD SUCCESSFUL` — zero errors, warnings are pre-existing deprecations unrelated to this work.

## Acceptance criteria
- AC-1: RemindersListScreen shows all reminders across vehicles with filter and delete ✅
- AC-2: ProfileScreen loads and saves first/last name ✅
