package org.mycarcompanion.app.platform

// Netlify redirects /auth/callback → /webapp/ so the Kotlin SDK (which stored the PKCE
// code verifier) can complete the exchange when the wasm app reloads at the callback URL.
actual val googleAuthRedirectUrl: String = "https://mycarcompanion.org/auth/callback?webapp=true"
