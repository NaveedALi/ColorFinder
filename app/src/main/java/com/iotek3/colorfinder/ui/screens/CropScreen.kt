package com.iotek3.colorfinder.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import com.iotek3.colorfinder.data.ImageRepository
import io.moyuru.cropify.Cropify
import java.io.File
import java.io.FileOutputStream
import io.moyuru.cropify.CropifyOption
import io.moyuru.cropify.CropifySize
import io.moyuru.cropify.rememberCropifyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    imageSource: String,
    onCropped: (String) -> Unit,
    onCancel: () -> Unit,
    saveToCache: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { ImageRepository(context) }
    val cropState = rememberCropifyState()

    val imageUri = when {
        imageSource.startsWith("/") -> Uri.parse("file://$imageSource")
        else -> Uri.parse(imageSource)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Crop image") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { cropState.crop() }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Cropify(
                uri = imageUri,
                state = cropState,
                onImageCropped = { imageBitmap ->
                    val bitmap = imageBitmap.asAndroidBitmap()
                    val path = if (saveToCache) {
                        val file = File(context.cacheDir, "colorfinder_cropped_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(file).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        file.absolutePath
                    } else {
                        repo.saveBitmapToFile(bitmap)?.absolutePath
                    }
                    path?.let { onCropped(it) }
                },
                onFailedToLoadImage = {
                    onCancel()
                },
                modifier = Modifier.fillMaxSize(),
                option = CropifyOption(
                    frameSize = CropifySize.PercentageSize.FullSize
                )
            )
        }
    }
}
