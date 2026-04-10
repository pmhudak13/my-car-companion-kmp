---
phase: 05-core-user-screens
plan: 02
status: complete
---

# Summary: SettingsScreen + VehicleSettingsScreen

## What was done

### VehicleSettingsScreenModel + VehicleSettingsScreen
- New `VehicleSettingsScreenModel` loads a vehicle by ID, populates `VehicleFormData`, handles save (via `VehicleRepository.updateVehicle`) and delete (via `VehicleRepository.deleteVehicle`) with confirmation dialog.
- `VehicleSettingsScreen(vehicleId)` — same make/model dropdown pattern as `AddVehicleScreen`. All fields editable. Delete button with AlertDialog confirmation. On save → pop. On delete → popUntilRoot.
- Wired into `VehicleDetailScreen` — gear icon button in `VehicleHeader` navigates to `VehicleSettingsScreen`.

### SettingsScreenModel + SettingsScreen
- `SettingsScreenModel` handles sign-out via `AuthRepository.signOut()`.
- `SettingsScreen` — hub with links to ProfileScreen, RemindersListScreen, sign-out confirmation dialog. On sign-out → replaceAll with LoginScreen.
- HomeScreen "Sign Out" button replaced with "Settings" button navigating to SettingsScreen.

### AppModule
- Added `factoryOf(::SettingsScreenModel)` and `factoryOf(::VehicleSettingsScreenModel)`

## Build result
`BUILD SUCCESSFUL` — zero errors.

## Acceptance criteria
- AC-1: VehicleSettingsScreen edits and saves vehicle fields ✅
- AC-2: VehicleSettingsScreen delete with confirmation removes vehicle ✅
- AC-3: SettingsScreen hub with profile/reminders/sign-out links ✅
