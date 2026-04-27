package org.mycarcompanion.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.koin.core.context.startKoin
import org.mycarcompanion.app.data.supabase.prewarmSupabaseClient
import org.mycarcompanion.app.di.appModule

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    try {
        startKoin {
            modules(appModule)
        }
        prewarmSupabaseClient()
        ComposeViewport(document.body!!) {
            App()
        }
    } catch (e: Throwable) {
        showFatalError(e.message ?: "Unknown startup error")
    }
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
