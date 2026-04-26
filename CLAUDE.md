# My Car Companion - Claude Memory

## Project
Kotlin Multiplatform (KMP) app targeting Android, iOS, and Web. Backend is Supabase (auth + PostgreSQL). UI is Compose Multiplatform. Navigation via Voyager. DI via Koin.

## Key files
- `shared/src/commonMain/kotlin/org/mycarcompanion/app/` — all shared business logic and UI
- `shared/.../data/repository/AuthRepository.kt` — auth state flow, sign-in/out, Google OAuth
- `shared/.../data/repository/ProfileRepository.kt` — role checks (`hasRole`), profile fetch
- `shared/.../ui/auth/LoginScreen.kt` — login UI, collects `authState` via `collectAsState`
- `androidApp/src/main/kotlin/.../MainActivity.kt` — Android entry point
- `webApp/src/wasmJsMain/kotlin/.../main.kt` — Web (wasmJs) entry point
- `shared/src/wasmJsMain/.../data/supabase/SupabaseConfig.wasmJs.kt` — reads Supabase URL/key from `window.__SUPABASE_URL__` / `window.__SUPABASE_ANON_KEY__` injected by the Netlify build step
- `shared/src/commonMain/.../data/supabase/SupabaseConfig.kt` — expect object; also declares platform-specific `authScheme`, `authHost`, `authAutoSaveToStorage`
- `shared/src/commonMain/.../data/supabase/SupabaseClientProvider.kt` — lazy `supabaseClient` val; uses `SupabaseConfig` for all Auth plugin config

## Known issues & patterns

### Auth state flow — ANR risk
`AuthRepository.authState` maps Supabase `sessionStatus` to `AuthState`.
On `SessionStatus.Authenticated` it fires 3 concurrent Supabase queries
(`hasRole("admin")`, `hasRole("mechanic")`, `getMyProfile`).

- **First fix (Apr 9 2026):** Added `flowOn(Dispatchers.Default)` + `coroutineScope { async {} }` to move work off the main thread and run queries in parallel.
- **Second fix (Apr 15 2026):** Changed `map` → `mapLatest` so that if `sessionStatus` emits again while queries are in flight (e.g. rapid Initializing → Authenticated on startup), the stale computation is cancelled instead of queuing up and starving `Dispatchers.Default`. This eliminated the recurring production ANR (`3e1c3f95e4ca41e092d59f3293629978`).

**Rule:** Always use `mapLatest` (not `map`) when the flow transform makes network/IO calls. Never use `map` with `coroutineScope` inside a flow that can emit rapidly.

**Note (web):** On wasmJs `Dispatchers.Default` is the same as `Dispatchers.Main` (single browser thread). The `flowOn`/`async` mitigations above have no parallelism effect on web — the 3 Supabase queries execute serially. This is a known limitation; no ANR risk on web but auth state takes longer to resolve.

### Web (wasmJs) crash fixes — Apr 26 2026
Seven crash/failure modes were identified and fixed in one commit:

1. **OAuth callback 404** (`netlify.toml`): Google OAuth redirects to `https://mycarcompanion.org/auth/callback` but no Netlify rule handled that path. Fixed by adding a `302` redirect `/auth/callback` → `/app/` so the PKCE auth code query param is forwarded to the running Compose app.
   - **Rule:** `googleAuthRedirectUrl` (`GoogleAuth.wasmJs.kt`) and `netlify.toml` redirect rules must stay in sync.

2. **`jsonPrimitive` crash** (`AuthRepository.kt:49`): `user.userMetadata?.get("role")?.jsonPrimitive` throws `IllegalArgumentException` when the `role` field in user metadata is a JSON object/array (not a primitive). Fixed to `(value as? JsonPrimitive)?.contentOrNull`.
   - **Rule:** Never use `.jsonPrimitive` directly on an unknown `JsonElement`; always safe-cast with `as? JsonPrimitive`.

3. **Silent startup crash** (`main.kt`): No `try/catch` around `startKoin`/`ComposeViewport` meant any exception left a frozen loading spinner with no user feedback. Fixed with a top-level `try/catch(Throwable)` that shows an error message + Reload button.

4. **Android deep-link scheme leaking onto web** (`SupabaseClientProvider.kt` / `SupabaseConfig`): `scheme = "org.mycarcompanion.app"` and `host = "auth"` were hardcoded in shared code; these are Android deep-link values meaningless (and potentially confusing) on web. Moved to `expect/actual` in `SupabaseConfig`: Android actuals keep the existing values; wasmJs actuals use empty strings.

5. **`localStorage` SecurityError** (`SupabaseConfig.wasmJs.kt`): `autoSaveToStorage = true` always called `localStorage`, which throws `SecurityError` in private browsing / strict browser security. Fixed via `authAutoSaveToStorage` actual that probes `localStorage` at startup and returns `false` when unavailable.

6. **First-frame stall** (`main.kt`): `supabaseClient` was not pre-warmed on web, so the first DI resolve (during first Compose frame) initialized Ktor on the single browser thread. Fixed by calling `prewarmSupabaseClient()` immediately after `startKoin`.

7. **Crashes invisible in production** (`index.html`): No JS error handlers meant Wasm exceptions were silent to the user. Fixed by adding `window.onerror` and `unhandledrejection` handlers that replace the spinner with an error + Reload button.

### Web deploy pipeline
- Build: `./gradlew :webApp:wasmJsBrowserDistribution` outputs to `webApp/build/dist/wasmJs/productionExecutable/`
- Key injection: GitHub Actions (`deploy-web.yml`) uses `sed` to replace `SUPABASE_ANON_KEY_PLACEHOLDER` in the built `index.html` with the `SUPABASE_ANON_KEY` secret. **Local dev requires manual replacement** — the placeholder causes 401 errors from Supabase.
- Deploy: `netlify-cli deploy --dir=site/public --prod` (Netlify, not Vercel)
- App is served at `https://mycarcompanion.org/app/`
- The `SUPABASE_URL` is hardcoded in the source `index.html` (not a secret). Only the anon key is injected at build time.

### Web-specific known limitations / open items
- Voyager `1.1.0-beta03` has known wasmJs screen-state and back-stack issues. The never-destroy Navigator pattern in `AppNavigation.kt` mitigates the auth-driven crash but Voyager beta on wasm is still fragile.
- No Sentry (or any crash reporter) on web — errors surface only through the `window.onerror` shim added above.
- Compose Resources (`Res.drawable.*`) on wasmJs load via `fetch()` from a relative `/composeResources/` path. If the app is served from a sub-path (e.g. `/app/`) the fetch resolves correctly only when the HTML base URL matches. Verify resource loading works at the deployed URL if images go missing.

## Build / run
- Android: run `androidApp` in Android Studio
- Web: `./gradlew :webApp:wasmJsBrowserDistribution` then serve `webApp/build/dist/wasmJs/productionExecutable/` (remember to replace `SUPABASE_ANON_KEY_PLACEHOLDER` in `index.html` for local dev)
- Shared module: `./gradlew :shared:build`
