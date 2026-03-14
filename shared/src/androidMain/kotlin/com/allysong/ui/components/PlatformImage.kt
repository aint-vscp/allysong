package com.allysong.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream

/** Max dimension (width or height) for compressed images. */
private const val MAX_IMAGE_DIMENSION = 320

/** JPEG compression quality (0-100). Lower = smaller payload. */
private const val JPEG_QUALITY = 50

@Composable
actual fun rememberImagePicker(onImageReady: (String) -> Unit): ImagePickerHandle {
    val context = LocalContext.current

    // Gallery picker — uses system SAF, no extra permissions needed
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = compressUriToBase64(context, uri)
            if (base64 != null) onImageReady(base64)
        }
    }

    // Camera — returns a small preview Bitmap directly (no FileProvider needed)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val base64 = compressBitmapToBase64(bitmap)
            onImageReady(base64)
        }
    }

    return ImagePickerHandle(
        launchGallery = { galleryLauncher.launch("image/*") },
        launchCamera = { cameraLauncher.launch(null) }
    )
}

@Composable
actual fun Base64Image(
    base64Data: String,
    contentDescription: String?,
    modifier: Modifier
) {
    val imageBitmap = remember(base64Data) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

// ── Compression helpers ─────────────────────────────────────────────────────

private fun compressUriToBase64(context: android.content.Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (original == null) return null
        compressBitmapToBase64(original)
    } catch (_: Exception) {
        null
    }
}

private fun compressBitmapToBase64(original: Bitmap): String {
    // Downscale to fit within MAX_IMAGE_DIMENSION
    val scaled = downscale(original, MAX_IMAGE_DIMENSION)

    // Compress to JPEG
    val outputStream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
    val jpegBytes = outputStream.toByteArray()

    // Base64 encode (NO_WRAP avoids line breaks which bloat the payload)
    return Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
}

private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDimension && height <= maxDimension) return bitmap

    val ratio = maxDimension.toFloat() / maxOf(width, height)
    val newWidth = (width * ratio).toInt().coerceAtLeast(1)
    val newHeight = (height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}
