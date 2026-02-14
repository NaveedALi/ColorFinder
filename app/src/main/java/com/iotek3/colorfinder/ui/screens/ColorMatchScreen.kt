package com.iotek3.colorfinder.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iotek3.colorfinder.data.ImageRepository
import com.iotek3.colorfinder.util.ColorExtractor
import java.io.File

/** Normalized rect: left, top, right, bottom in 0-1. */
data class MatchResult(val file: File, val score: Double, val matchRect: FloatArray? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorMatchScreen(
    tempImagePath: String,
    onBack: () -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { ImageRepository(context) }
    var objectColors by remember { mutableStateOf<List<androidx.compose.ui.graphics.Color>>(emptyList()) }
    var matchingImages by remember { mutableStateOf<List<MatchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(tempImagePath) {
        onDispose {
            File(tempImagePath).delete()
        }
    }
    LaunchedEffect(tempImagePath) {
        val tempFile = File(tempImagePath)
        if (!tempFile.exists()) {
            loading = false
            return@LaunchedEffect
        }
        val bitmap = repo.bitmapFromFile(tempFile) ?: run {
            loading = false
            return@LaunchedEffect
        }
        val rgbColors = ColorExtractor.extractColorsAsRgb(bitmap, 5)
        objectColors = rgbColors.map { androidx.compose.ui.graphics.Color(android.graphics.Color.rgb(it.r, it.g, it.b)) }
        val saved = repo.listSavedImages()
        matchingImages = saved.map { file ->
            val savedBitmap = repo.bitmapFromFile(file)
            if (savedBitmap == null) return@map MatchResult(file, Double.MAX_VALUE, null)
            val savedRgb = ColorExtractor.extractColorsAsRgb(savedBitmap, 6)
            val bestTarget = rgbColors.minByOrNull { tc ->
                savedRgb.minOf { ic -> ColorExtractor.colorDistance(tc, ic) }
            } ?: rgbColors.first()
            val score = ColorExtractor.bestMatchScore(rgbColors, savedRgb)
            val rect = ColorExtractor.findMatchingRegion(savedBitmap, bestTarget, 8)
            MatchResult(file, score, rect)
        }.sortedBy { it.score }
        loading = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Images with this color") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                AsyncImage(
                    model = File(tempImagePath),
                    contentDescription = "Captured object",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = "Object color",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                objectColors.take(6).forEach { color ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = color),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    ) {}
                }
            }
            Text(
                text = "Saved images with this color",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Finding matches…", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (matchingImages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No saved images. Add images in Folder first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(matchingImages) { result ->
                        Card(
                            onClick = { onImageClick(result.file.absolutePath) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = CardDefaults.shape
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = result.file,
                                    contentDescription = result.file.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                result.matchRect?.let { rect ->
                                    val strokeColor = MaterialTheme.colorScheme.primary
                                    val strokeWidthPx = 3.dp
                                    Canvas(Modifier.fillMaxSize()) {
                                        val left = size.width * rect[0]
                                        val top = size.height * rect[1]
                                        val right = size.width * rect[2]
                                        val bottom = size.height * rect[3]
                                        drawRect(
                                            color = strokeColor,
                                            topLeft = Offset(left, top),
                                            size = Size(right - left, bottom - top),
                                            style = Stroke(width = strokeWidthPx.toPx())
                                        )
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
    }
}
