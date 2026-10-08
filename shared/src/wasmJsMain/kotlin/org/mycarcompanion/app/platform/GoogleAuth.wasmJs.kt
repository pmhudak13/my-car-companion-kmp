package org.mycarcompanion.app.platform

// Netlify redirects /auth/callback → /webapp/ so the Kotlin SDK (which stored the PKCE
// code verifier) can complete the exchange when the wasm app reloads at the callback URL.
actual val googleAuthRedirectUrl: String = "https://mycarcompanion.org/auth/callback?webapp=true"

// Same callback as Google: Netlify sends it to /webapp/, which reads the session from the URL.
// Without this the link used the Site URL and landed new users on the old /app/ page.
actual val signUpRedirectUrl: String? = googleAuthRedirectUrl
