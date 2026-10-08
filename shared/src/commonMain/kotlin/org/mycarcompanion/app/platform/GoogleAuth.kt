package org.mycarcompanion.app.platform

expect val googleAuthRedirectUrl: String

/** Where the sign-up confirmation email link lands; null keeps the Supabase Site URL. */
expect val signUpRedirectUrl: String?
