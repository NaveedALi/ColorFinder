package com.iotek3.colorfinder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iotek3.colorfinder.data.ImageRepository
import com.iotek3.colorfinder.ui.screens.ColorGridScreen
import com.iotek3.colorfinder.ui.screens.ColorMatchScreen
import com.iotek3.colorfinder.ui.screens.CropScreen
import com.iotek3.colorfinder.ui.screens.FolderScreen
import com.iotek3.colorfinder.ui.screens.HomeScreen
import com.iotek3.colorfinder.ui.theme.ColorFinderTheme
import java.io.File

private fun launchCamera(
    context: android.content.Context,
    useTempFile: Boolean,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    cameraLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri>,
    setLastCameraFile: (File) -> Unit
) {
    val file = if (useTempFile) {
        File(context.cacheDir, "colorfinder_capture_${System.currentTimeMillis()}.jpg")
    } else {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            ?: context.filesDir
        val colorFinder = File(dir, ImageRepository.FOLDER_NAME).apply { mkdirs() }
        File(colorFinder, "capture_${System.currentTimeMillis()}.jpg")
    }
    if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        setLastCameraFile(file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    } else {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ColorFinderApp()
        }
    }

}

@Composable
private fun ColorFinderApp() {
    ColorFinderTheme {
        val navController = rememberNavController()
        val context = LocalContext.current
        //val repo = remember { ImageRepository(context) }
        var folderRefreshKey by remember { mutableStateOf(0L) }
        var lastCameraFile: File? by remember { mutableStateOf(null) }
        var cameraPopUpToRoute by remember { mutableStateOf("home") }
        var cameraDestination by remember { mutableStateOf("crop") }

        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                lastCameraFile?.absolutePath?.let { path ->
                    val encoded = Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                    navController.navigate("$cameraDestination/$encoded") {
                        popUpTo(cameraPopUpToRoute) { inclusive = false }
                    }
                }
            }
        }

        val pickMultipleMedia = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(9)
        ) { uris ->
            if (uris.isNotEmpty()) {
                val firstUri = uris.first().toString()
                val encoded = Base64.encodeToString(firstUri.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                navController.navigate("crop/$encoded") {
                    popUpTo("folder/${folderRefreshKey}") { inclusive = false }
                }
                folderRefreshKey = System.currentTimeMillis()
            }
        }

        var cameraUseTempFile by remember { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { granted ->
            if (granted[Manifest.permission.CAMERA] == true) {
                val file = if (cameraUseTempFile) {
                    File(context.cacheDir, "colorfinder_capture_${System.currentTimeMillis()}.jpg")
                } else {
                    val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                        ?: context.filesDir
                    val colorFinder = File(dir, ImageRepository.FOLDER_NAME).apply { mkdirs() }
                    File(colorFinder, "capture_${System.currentTimeMillis()}.jpg")
                }
                lastCameraFile = file
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraLauncher.launch(uri)
            }
        }


        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("home") {
                HomeScreen(
                    onCameraClick = {
                        cameraPopUpToRoute = "home"
                        cameraDestination = "cropMatch"
                        cameraUseTempFile = true
                        launchCamera(context, useTempFile = true, permissionLauncher, cameraLauncher) { file ->
                            lastCameraFile = file
                        }
                    },
                    onFolderClick = {
                        navController.navigate("folder/$folderRefreshKey") {
                            popUpTo("home") { inclusive = false }
                        }
                    }
                )
            }

            composable("cropMatch/{encodedSource}") { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString("encodedSource").orEmpty()
                val imageSource = decodeBase64ToString(encoded)
                CropScreen(
                    imageSource = imageSource,
                    saveToCache = true,
                    onCropped = { path ->
                        File(imageSource).delete()
                        val pathEncoded = Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                        navController.navigate("colorMatch/$pathEncoded") {
                            popUpTo("cropMatch/$encoded") { inclusive = true }
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable("colorMatch/{encodedPath}") { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString("encodedPath").orEmpty()
                val tempPath = decodeBase64ToString(encoded)
                ColorMatchScreen(
                    tempImagePath = tempPath,
                    onBack = { navController.popBackStack() },
                    onImageClick = { path ->
                        val pathEncoded = Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                        navController.navigate("colorGrid/$pathEncoded") {
                            popUpTo("colorMatch/$encoded") { inclusive = false }
                        }
                    }
                )
            }

            composable("crop/{encodedSource}") { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString("encodedSource").orEmpty()
                val imageSource = decodeBase64ToString(encoded)
                CropScreen(
                    imageSource = imageSource,
                    onCropped = { path ->
                        val pathEncoded = Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                        navController.navigate("colorGrid/$pathEncoded") {
                            popUpTo("crop/$encoded") { inclusive = true }
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable("folder/{refreshKey}") { backStackEntry ->
                val refreshKey = backStackEntry.arguments?.getString("refreshKey").orEmpty()
                val folderRoute = "folder/${refreshKey.ifEmpty { "0" }}"
                FolderScreen(
                    onBack = { navController.popBackStack() },
                    onAddFromCamera = {
                        cameraPopUpToRoute = folderRoute
                        cameraDestination = "crop"
                        cameraUseTempFile = false
                        launchCamera(context, useTempFile = false, permissionLauncher, cameraLauncher) { file ->
                            lastCameraFile = file
                        }
                    },
                    onAddFromGallery = {
                        pickMultipleMedia.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onImageClick = { path ->
                        val encoded = Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                        navController.navigate("colorGrid/$encoded") {
                            popUpTo(folderRoute) { inclusive = false }
                        }
                    },
                    refreshKey = refreshKey.ifEmpty { "0" }
                )
            }

            composable("colorGrid/{imagePath}") { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString("imagePath").orEmpty()
                val imagePath = decodeBase64ToString(encoded)
                ColorGridScreen(
                    imagePath = imagePath,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

fun decodeBase64ToString(encoded: String): String {
    if (encoded.isEmpty()) return ""
    return try {
        val bytes = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP)
        String(bytes, Charsets.UTF_8)
    } catch (_: Exception) {
        ""
    }
}
