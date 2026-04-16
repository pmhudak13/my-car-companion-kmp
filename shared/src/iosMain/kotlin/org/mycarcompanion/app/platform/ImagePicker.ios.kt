package org.mycarcompanion.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberImagePickerLauncher(
    onImagePicked: (ByteArray?) -> Unit,
): ImagePickerLauncher {
    val callback = remember(onImagePicked) { onImagePicked }
    return remember {
        ImagePickerLauncher {
            val config = PHPickerConfiguration().apply {
                filter = PHPickerFilter.imagesFilter()
                selectionLimit = 1
            }
            val picker = PHPickerViewController(configuration = config)
            val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                override fun picker(
                    picker: PHPickerViewController,
                    didFinishPicking: List<*>,
                ) {
                    picker.dismissViewControllerAnimated(true, null)
                    val result = didFinishPicking.firstOrNull() as? PHPickerResult
                    if (result == null) {
                        callback(null)
                        return
                    }
                    result.itemProvider.loadDataRepresentationForTypeIdentifier(
                        typeIdentifier = "public.image",
                    ) { data: NSData?, _ ->
                        if (data != null) {
                            val image = UIImage(data = data)
                            val jpegData = UIImageJPEGRepresentation(image, 0.85)
                            if (jpegData != null) {
                                val bytes = ByteArray(jpegData.length.toInt())
                                bytes.usePinned { pinned ->
                                    platform.posix.memcpy(
                                        pinned.addressOf(0),
                                        jpegData.bytes,
                                        jpegData.length,
                                    )
                                }
                                callback(bytes)
                            } else {
                                callback(null)
                            }
                        } else {
                            callback(null)
                        }
                    }
                }
            }
            picker.delegate = delegate
            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}
