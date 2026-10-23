package io.github.mockuptools.tinyworks.feature.scooter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Decode to a bounded image, strip metadata, and keep an app-private copy independent of the source. */
internal fun importScooterPhoto(context: Context, uri: Uri): String {
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val scale = minOf(1.0, 1280.0 / maxOf(info.size.width, info.size.height))
        decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val directory = File(context.filesDir, "scooter_photos").apply { check(isDirectory || mkdirs()) }
    val file = File(directory, "${UUID.randomUUID()}.jpg")
    try {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
        return file.name
    } catch (error: Exception) {
        file.delete()
        throw error
    } finally { bitmap.recycle() }
}

internal fun deleteScooterPhoto(context: Context, name: String?) {
    if (name != null && name == File(name).name) File(context.filesDir, "scooter_photos/$name").delete()
}

@Composable
internal fun ScooterPhoto(name: String?, model: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                require(name != null && name == File(name).name)
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(context.filesDir, "scooter_photos/$name"))) { decoder, info, _ ->
                    val scale = minOf(1.0, 400.0 / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
                }.asImageBitmap()
            }.getOrNull()
        }
    }.value
    Box(modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, model, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Text("🛵", fontSize = 36.sp)
    }
}
