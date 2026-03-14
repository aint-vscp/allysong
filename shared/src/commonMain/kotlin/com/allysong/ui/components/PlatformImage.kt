package com.allysong.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Launcher handle returned by [rememberImagePicker].
 * Provides platform-agnostic callbacks to open gallery or camera.
 */
data class ImagePickerHandle(
    val launchGallery: () -> Unit,
    val launchCamera: () -> Unit
)

/**
 * Remembers a platform image picker that compresses the selected/captured
 * image to a small JPEG and returns the result as a base64 string.
 *
 * @param onImageReady Called with the base64-encoded JPEG after compression.
 */
@Composable
expect fun rememberImagePicker(onImageReady: (String) -> Unit): ImagePickerHandle

/**
 * Displays a base64-encoded JPEG image with remembered decoding.
 */
@Composable
expect fun Base64Image(
    base64Data: String,
    contentDescription: String?,
    modifier: Modifier
)
