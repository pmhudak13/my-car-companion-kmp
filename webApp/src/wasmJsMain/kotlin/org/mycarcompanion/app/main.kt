package org.mycarcompanion.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
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

        // Read session handoff from sessionStorage (written by /app/ page).
        // sessionStorage is tab-scoped and never appears in browser history or Referer headers.
        // Fall back to URL hash for backwards compatibility with any existing deep-links.
        val handoffSession = parseStorageSession() ?: parseHashSession()
        if (handoffSession != null) {
            if (window.location.hash.contains("access_token=")) {
                // Clean legacy hash tokens from URL
                cleanHashFromUrl()
            }
            // Import the session before Compose starts so the app opens already authenticated.
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    importSessionTokens(handoffSession.first, handoffSession.second)
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
        // window.open(url, "_blank") is called from an async coroutine context, so
        // popup blockers reject it silently. Try _blank; if it returns null (blocked),
        // fall back to same-tab navigation so Stripe checkout always opens.
        CompositionLocalProvider(LocalUriHandler provides WebUriHandler) {
            App()
        }
    }
}

private object WebUriHandler : UriHandler {
    override fun openUri(uri: String) {
        val opened = window.open(uri, "_blank")
        if (opened == null) {
            window.location.href = uri
        }
    }
}

// Kotlin/Wasm requires js() to be the single expression of a top-level function —
// it cannot appear inside a try/catch or other nested block. Each call is therefore
// isolated in its own function and called from parseStorageSession() below.
private fun jsReadHandoff(): JsAny? =
    js("(function(){ try { return sessionStorage.getItem('_mcc_handoff'); } catch(e){ return null; } })()")

private fun jsClearHandoff(): JsAny? =
    js("(function(){ try { sessionStorage.removeItem('_mcc_handoff'); } catch(e){} })()")

/** Reads session tokens written by /app/ via sessionStorage and clears them immediately. */
private fun parseStorageSession(): Pair<String, String>? {
    return try {
        val raw = jsReadHandoff() as? String ?: return null
        jsClearHandoff()
        val json = raw.trimStart('{').trimEnd('}')
        fun extractField(key: String): String? {
            val marker = "\"$key\":"
            val start = json.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
            val valueStart = json.indexOf('"', start).takeIf { it >= 0 }?.plus(1) ?: return null
            val valueEnd = json.indexOf('"', valueStart).takeIf { it >= 0 } ?: return null
            return json.substring(valueStart, valueEnd).ifBlank { null }
        }
        val accessToken = extractField("access_token") ?: return null
        val refreshToken = extractField("refresh_token") ?: ""
        accessToken to refreshToken
    } catch (_: Throwable) {
        null
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
