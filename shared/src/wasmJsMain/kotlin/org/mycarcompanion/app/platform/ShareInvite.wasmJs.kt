package org.mycarcompanion.app.platform

actual fun shareText(title: String, text: String) {
    js("if (typeof navigator !== 'undefined' && navigator.share) { navigator.share({ title: title, text: text }); }")
}
