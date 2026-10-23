package org.mycarcompanion.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private fun jsDownloadCsv(fileName: String, content: String): Unit = js("""(function() {
    var url = URL.createObjectURL(new Blob([content], {type: 'text/csv;charset=utf-8'}));
    var a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(function() { URL.revokeObjectURL(url); }, 1000);
})()""")

@Composable
actual fun rememberCsvFileSaver(): FileSaver = remember {
    object : FileSaver {
        override fun save(fileName: String, content: String) = jsDownloadCsv(fileName, content)
    }
}
