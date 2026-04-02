# My Car Companion — KMP Rewrite

Kotlin Multiplatform (Compose Multiplatform) rewrite of the My Car Companion app.
Targets Android (primary), WebAssembly/Web, and iOS (scaffolded).

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.1.0 |
| UI | Compose Multiplatform 1.7.3 |
| Backend | Supabase (Auth + Postgrest + Storage + Realtime) |
| DI | Koin 3.5.6 |
| Navigation | Voyager 1.1.0-rc03 |
| Networking | Ktor 3.0.3 |
| Image Loading | Coil3 3.0.4 |

## Project Structure

```
shared/         # ALL composables, ViewModels (ScreenModels), repositories, data models
androidApp/     # Thin wrapper — MainActivity only
webApp/         # Thin wrapper — WASM entry point only
iosApp/         # Xcode project stub (Phase 8)
```

## Setup

### 1. Prerequisites

- JDK 17+
- Android Studio Hedgehog or newer
- Android SDK (API 26+)

### 2. Configure local.properties

Copy the example file and fill in your values:

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
sdk.dir=/path/to/your/android/sdk
SUPABASE_URL=https://eoqycogokelfmgwqydkj.supabase.co
SUPABASE_ANON_KEY=your_actual_supabase_anon_key_here
```

> **Never commit `local.properties`** — it is gitignored.

### 3. Build the Android app

```bash
./gradlew :androidApp:assembleDebug
```

APK will be at: `androidApp/build/outputs/apk/debug/`

### 4. Run on Android device/emulator

```bash
./gradlew :androidApp:installDebug
```

### 5. Build the Web app (WASM)

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

## GitHub Actions

The CI workflow (`.github/workflows/android-ci.yml`) builds a debug APK on every push/PR to `main`.

Required repository secrets:
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`

---

## 8-Phase Delivery Plan

| Phase | Description | Status |
|-------|-------------|--------|
| **1** | KMP project foundation, Supabase auth (login/signup/signout) | Done |
| **2** | Vehicle list + add vehicle screen, Supabase `vehicles` table | Pending |
| **3** | Maintenance records (oil change, tires, etc.), CRUD screens | Pending |
| **4** | Push notifications / reminders (maintenance due dates) | Pending |
| **5** | GPS mileage tracker (Android Capacitor parity feature) | Pending |
| **6** | Shop type + multi-shop per vehicle, referral tracking | Pending |
| **7** | Web (WASM) production build + Vercel deploy | Pending |
| **8** | iOS full implementation (Ktor Darwin, Info.plist secrets) | Pending |
