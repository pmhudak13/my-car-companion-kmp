# My Car Companion - Claude Memory

## Project
Kotlin Multiplatform (KMP) app targeting Android, iOS, and Web. Backend is Supabase (auth + PostgreSQL). UI is Compose Multiplatform. Navigation via Voyager. DI via Koin.

## Key files
- `shared/src/commonMain/kotlin/org/mycarcompanion/app/` — all shared business logic and UI
- `shared/.../data/repository/AuthRepository.kt` — auth state flow, sign-in/out, Google OAuth
- `shared/.../data/repository/ProfileRepository.kt` — role checks (`hasRole`), profile fetch
- `shared/.../ui/auth/LoginScreen.kt` — login UI, collects `authState` via `collectAsState`
- `androidApp/src/main/kotlin/.../MainActivity.kt` — Android entry point

## Known issues & patterns

### Auth state flow — ANR risk
`AuthRepository.authState` maps Supabase `sessionStatus` to `AuthState`.
On `SessionStatus.Authenticated` it fires 3 concurrent Supabase queries
(`hasRole("admin")`, `hasRole("mechanic")`, `getMyProfile`).

- **First fix (Apr 9 2026):** Added `flowOn(Dispatchers.Default)` + `coroutineScope { async {} }` to move work off the main thread and run queries in parallel.
- **Second fix (Apr 15 2026):** Changed `map` → `mapLatest` so that if `sessionStatus` emits again while queries are in flight (e.g. rapid Initializing → Authenticated on startup), the stale computation is cancelled instead of queuing up and starving `Dispatchers.Default`. This eliminated the recurring production ANR (`3e1c3f95e4ca41e092d59f3293629978`).

**Rule:** Always use `mapLatest` (not `map`) when the flow transform makes network/IO calls. Never use `map` with `coroutineScope` inside a flow that can emit rapidly.

## Build / run
- Android: run `androidApp` in Android Studio
- Shared module: `./gradlew :shared:build`
