package org.mycarcompanion.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.mycarcompanion.app.data.supabase.importSessionTokens
import org.mycarcompanion.app.data.supabase.prewarmSupabaseClient
import org.mycarcompanion.app.di.appModule

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    try {
        startKoin {
            modules(appModule)
        }
        prewarmSupabaseClient()

        val hashSession = parseHashSession()
        if (hashSession != null) {
            // Remove tokens from the URL immediately so they don't appear in history or Referer headers
            cleanHashFromUrl()
            // Import the session before Compose starts so the app opens already authenticated.
            // The HTML loading spinner stays visible while the HTTP round-trip completes.
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    importSessionTokens(hashSession.first, hashSession.second)
                } catch (_: Throwable) {
                    // Token import failed (e.g. expired) — app will show login screen normally
                }
                startCompose()
            }
        } else {
            startCompose()
        }
    } catch (e: Throwable) {
        showFatalError(e.message ?: "Unknown startup error")
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun startCompose() {
    ComposeViewport(document.body!!) {
        App()
    }
}

/** Returns (accessToken, refreshToken) from the URL hash, or null if not present. */
private fun parseHashSession(): Pair<String, String>? {
    val hash = window.location.hash.trimStart('#')
    if (hash.isEmpty() || !hash.contains("access_token=")) return null
    val params = hash.split("&").associate { part ->
        val idx = part.indexOf('=')
        if (idx < 0) part to "" else part.substring(0, idx) to part.substring(idx + 1)
    }
    val accessToken = params["access_token"] ?: return null
    val refreshToken = params["refresh_token"] ?: return null
    return accessToken to refreshToken
}

private fun cleanHashFromUrl() {
    js("history.replaceState(null, '', window.location.pathname + window.location.search)")
}

private fun showFatalError(message: String) {
    val loading = document.getElementById("loading")
    if (loading != null) {
        loading.innerHTML = """
            <div style="text-align:center;padding:32px;max-width:480px">
                <p style="color:#FF6B6B;font-size:18px;margin-bottom:12px">Something went wrong</p>
                <p style="color:#aaa;font-size:13px;word-break:break-word">$message</p>
                <button onclick="location.reload()" style="margin-top:20px;padding:10px 24px;background:#6BBCDE;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:14px">Reload</button>
            </div>
        """.trimIndent()
    }
}
