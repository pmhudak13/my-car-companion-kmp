package org.mycarcompanion.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

// ── JS bridge (installed once at module init) ─────────────────────────────────
// js() in Kotlin/Wasm may only appear as a top-level property initializer
// or as the sole expression in a top-level function body.

@Suppress("unused")
private val _bridge: Boolean = js("""(function() {
    if (window.__kmpFilePicker) return true;
    window.__kmpFilePicker = {
        result: null,
        pickText: function() {
            this.result = null;
            var self = this;
            var input = document.createElement('input');
            input.type = 'file';
            input.accept = '.csv,text/csv,text/plain';
            input.style.display = 'none';
            document.body.appendChild(input);
            input.addEventListener('change', function(e) {
                document.body.removeChild(input);
                var file = e.target.files[0];
                if (!file) { self.result = {done: true, cancelled: true}; return; }
                var reader = new FileReader();
                reader.onload = function(ev) {
                    self.result = {done: true, name: file.name, value: ev.target.result};
                };
                reader.readAsText(file);
            });
            input.addEventListener('cancel', function() {
                if (document.body.contains(input)) document.body.removeChild(input);
                self.result = {done: true, cancelled: true};
            });
            input.click();
        },
        pickBinary: function() {
            this.result = null;
            var self = this;
            var input = document.createElement('input');
            input.type = 'file';
            input.accept = 'image/*,application/pdf';
            input.style.display = 'none';
            document.body.appendChild(input);
            input.addEventListener('change', function(e) {
                document.body.removeChild(input);
                var file = e.target.files[0];
                if (!file) { self.result = {done: true, cancelled: true}; return; }
                var reader = new FileReader();
                reader.onload = function(ev) {
                    self.result = {done: true, name: file.name, value: ev.target.result, mime: file.type || 'image/jpeg'};
                };
                reader.readAsDataURL(file);
            });
            input.addEventListener('cancel', function() {
                if (document.body.contains(input)) document.body.removeChild(input);
                self.result = {done: true, cancelled: true};
            });
            input.click();
        }
    };
    return true;
})()""")

// ── Top-level JS accessors (the only valid placement for js() calls) ──────────

private fun jsPickText(): Unit = js("(window.__kmpFilePicker && (window.__kmpFilePicker.result = null, window.__kmpFilePicker.pickText()))")
private fun jsPickBinary(): Unit = js("(window.__kmpFilePicker && (window.__kmpFilePicker.result = null, window.__kmpFilePicker.pickBinary()))")
private fun jsIsDone(): Boolean = js("!!(window.__kmpFilePicker && window.__kmpFilePicker.result && window.__kmpFilePicker.result.done)")
private fun jsIsCancelled(): Boolean = js("!!(window.__kmpFilePicker && window.__kmpFilePicker.result && window.__kmpFilePicker.result.cancelled)")
private fun jsGetName(): JsString = js("(window.__kmpFilePicker && window.__kmpFilePicker.result && window.__kmpFilePicker.result.name) || 'file'")
private fun jsGetTextValue(): JsString = js("(window.__kmpFilePicker && window.__kmpFilePicker.result && window.__kmpFilePicker.result.value) || ''")
private fun jsGetDataUrl(): JsString = js("(window.__kmpFilePicker && window.__kmpFilePicker.result && window.__kmpFilePicker.result.value) || ''")
private fun jsGetMime(): JsString = js("(window.__kmpFilePicker && window.__kmpFilePicker.result && window.__kmpFilePicker.result.mime) || 'image/jpeg'")
private fun jsClearResult(): Unit = js("(window.__kmpFilePicker && (window.__kmpFilePicker.result = null))")

// ── Composable actuals ────────────────────────────────────────────────────────

@Composable
actual fun rememberTextFilePickerLauncher(
    onResult: (fileName: String, content: String) -> Unit,
): FilePickerLauncher {
    val callback = rememberUpdatedState(onResult)
    val isPicking = remember { mutableStateOf(false) }

    LaunchedEffect(isPicking.value) {
        if (!isPicking.value) return@LaunchedEffect
        while (true) {
            delay(150)
            if (jsIsDone()) {
                if (!jsIsCancelled()) {
                    val name = jsGetName().toString()
                    val content = jsGetTextValue().toString()
                    callback.value(name, content)
                }
                jsClearResult()
                isPicking.value = false
                break
            }
        }
    }

    return remember {
        object : FilePickerLauncher {
            override fun launch() {
                jsPickText()
                isPicking.value = true
            }
        }
    }
}

@Composable
actual fun rememberBinaryFilePickerLauncher(
    onResult: (fileName: String, base64: String, mimeType: String) -> Unit,
): FilePickerLauncher {
    val callback = rememberUpdatedState(onResult)
    val isPicking = remember { mutableStateOf(false) }

    LaunchedEffect(isPicking.value) {
        if (!isPicking.value) return@LaunchedEffect
        while (true) {
            delay(150)
            if (jsIsDone()) {
                if (!jsIsCancelled()) {
                    val name = jsGetName().toString()
                    val dataUrl = jsGetDataUrl().toString()
                    val mime = jsGetMime().toString()
                    // dataUrl = "data:image/jpeg;base64,/9j/..." — strip the prefix
                    val base64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
                    callback.value(name, base64, mime)
                }
                jsClearResult()
                isPicking.value = false
                break
            }
        }
    }

    return remember {
        object : FilePickerLauncher {
            override fun launch() {
                jsPickBinary()
                isPicking.value = true
            }
        }
    }
}
