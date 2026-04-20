package org.mycarcompanion.app.platform

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual fun shareText(title: String, text: String) {
    val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val activityVC = UIActivityViewController(listOf(text), null)
    rootVC.presentViewController(activityVC, animated = true, completion = null)
}
