package org.mycarcompanion.app.platform

actual val googleAuthRedirectUrl: String = "org.mycarcompanion.app://auth/callback"

// ponytail: mobile keeps the website confirmation page; deep-link it into the app if users ask
actual val signUpRedirectUrl: String? = null
