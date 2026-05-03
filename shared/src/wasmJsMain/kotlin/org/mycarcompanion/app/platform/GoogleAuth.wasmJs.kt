package org.mycarcompanion.app.platform

// ?webapp=true tells /app/ to auto-redirect back to /webapp/ after the PKCE exchange.
actual val googleAuthRedirectUrl: String = "https://mycarcompanion.org/auth/callback?webapp=true"
