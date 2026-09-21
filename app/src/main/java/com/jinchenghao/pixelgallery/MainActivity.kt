@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jinchenghao.pixelgallery

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private val Ink = Color(0xFF0B0D12)
private val Panel = Color(0xFF151922)
private val Accent = Color(0xFF9D8CFF)

data class LocalPhoto(
    val id: Long,
    val uri: android.net.Uri,
    val name: String,
    val width: Int,
    val height: Int,
    val date: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PixelGalleryApp() }
    }
}

@Composable
fun PixelGalleryApp() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Ink, surface = Panel)) {
        val context = LocalContext.current
        var granted by remember { mutableStateOf(hasImagePermission(context)) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
        }
        LaunchedEffect(Unit) {
            if (!granted) launcher.launch(imagePermission())
        }
        if (granted) Gallery() else PermissionPage { launcher.launch(imagePermission()) }
    }
}

private fun imagePermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasImagePermission(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, imagePermission()) == PackageManager.PERMISSION_GRANTED

@Composable
fun PermissionPage(onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.PhotoLibrary, null, tint = Accent, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("访问本地照片", style = MaterialTheme.typography.headlineSmall)
                Text("用于展示设备中的图片，不会上传任何内容。", color = Color.LightGray, modifier = Modifier.padding(vertical = 12.dp))
                Button(onClick = onGrant) { Text("允许访问") }
            }
        }
    }
}

@Composable
private fun rememberOriginalImageRequest(uri: android.net.Uri): ImageRequest {
    val context = LocalContext.current
    return remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(Size.ORIGINAL)
            .build()
    }
}

@Composable
fun Gallery() {
    val context = LocalContext.current
    var folderPhotos by remember { mutableStateOf<List<LocalPhoto>?>(null) }
    var folderLoading by remember { mutableStateOf(false) }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            folderLoading = true
            kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                folderPhotos = loadFolderPhotos(context, treeUri)
                folderLoading = false
            }
        }
    }
    var photos by remember { mutableStateOf<List<LocalPhoto>>(emptyList()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var columns by remember { mutableIntStateOf(4) }
    var maxPixelSize by remember { mutableStateOf<Int?>(null) }
    val sourcePhotos = folderPhotos ?: photos
    val visiblePhotos = remember(sourcePhotos, maxPixelSize) {
        maxPixelSize?.let { limit ->
            sourcePhotos.filter { it.width in 1..limit && it.height in 1..limit }
        } ?: sourcePhotos
    }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val filterSizes = remember { listOf(32, 64, 128, 256, 512, 1024, 2048) }
    var pinch by remember { mutableFloatStateOf(1f) }
    val galleryGridState = rememberLazyGridState()

    LaunchedEffect(Unit) { photos = loadPhotos(context) }
    BackHandler(enabled = selected != null) { selected = null }
    if (selected != null) {
        Viewer(visiblePhotos, selected!!, { selected = it }, { selected = null })
        return
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
                title = {
                    Column {
                        Text("Pixel Gallery", style = MaterialTheme.typography.titleLarge)
                        Text("${visiblePhotos.size}/${sourcePhotos.size} photos • 捏合调整网格", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    }
                    if (folderPhotos != null) {
                        IconButton(onClick = { folderPhotos = null; selected = null }) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                        }
                    }
                    Box(modifier = Modifier.padding(end = 12.dp)) {
                        FilterChip(
                            selected = maxPixelSize != null,
                            onClick = { filterMenuExpanded = true },
                            label = { Text(maxPixelSize?.let { "${it}px" } ?: "全部") },
                            leadingIcon = {
                                Icon(
                                    if (maxPixelSize != null) Icons.Rounded.FilterAlt else Icons.Rounded.FilterAltOff,
                                    contentDescription = "尺寸筛选"
                                )
                            },
                            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) }
                        )
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部尺寸") },
                                leadingIcon = { if (maxPixelSize == null) Icon(Icons.Rounded.Check, null) },
                                onClick = {
                                    maxPixelSize = null
                                    selected = null
                                    filterMenuExpanded = false
                                }
                            )
                            filterSizes.forEach { size ->
                                DropdownMenuItem(
                                    text = { Text("不超过 ${size} × ${size}px") },
                                    leadingIcon = { if (maxPixelSize == size) Icon(Icons.Rounded.Check, null) },
                                    onClick = {
                                        maxPixelSize = size
                                        selected = null
                                        filterMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (folderLoading) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } else if (visiblePhotos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyVerticalGrid(
                state = galleryGridState,
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize().padding(padding).pointerInput(columns) {
                    detectTransformGestures { _, _, zoom, _ ->
                        pinch *= zoom
                        if (pinch > 1.18f && columns > 2) { columns--; pinch = 1f }
                        if (pinch < 0.82f && columns < 12) { columns++; pinch = 1f }
                    }
                }
            ) {
                itemsIndexed(visiblePhotos, key = { _, photo -> photo.id }) { index, photo ->
                    val imageRequest = rememberOriginalImageRequest(photo.uri)
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = photo.name,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.aspectRatio(1f)
                            .clip(RoundedCornerShape(if (columns <= 3) 12.dp else 3.dp))
                            .pointerInput(index) { detectTapGestures { selected = index } }
                    )
                }
            }
        }
    }
}

@Composable
fun Viewer(photos: List<LocalPhoto>, initial: Int, onIndex: (Int) -> Unit, onBack: () -> Unit) {
    var index by remember { mutableIntStateOf(initial) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var chrome by remember { mutableStateOf(true) }
    val photo = photos[index]
    val imageRequest = rememberOriginalImageRequest(photo.uri)
    fun reset() { scale = 1f; offset = Offset.Zero }

    Box(Modifier.fillMaxSize().background(Ink)) {
        AsyncImage(
            model = imageRequest,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .pointerInput(index, viewportSize) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(1f, 64f)
                        if (newScale <= 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val viewportCenter = Offset(
                                viewportSize.width / 2f,
                                viewportSize.height / 2f
                            )
                            val focalPoint = centroid - viewportCenter
                            val scaleRatio = newScale / oldScale
                            offset = focalPoint + pan - (focalPoint - offset) * scaleRatio
                            scale = newScale
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(index) {
                    detectTapGestures(
                        onTap = { chrome = !chrome },
                        onDoubleTap = { if (scale > 1f) reset() else scale = 8f }
                    )
                }
        )

        AnimatedVisibility(chrome, Modifier.align(Alignment.TopCenter)) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xCC0B0D12)),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) } },
                title = {
                    Column {
                        Text(photo.name, maxLines = 1)
                        Text("${photo.width}×${photo.height} • ${index + 1}/${photos.size}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            )
        }

        AnimatedVisibility(chrome, Modifier.align(Alignment.BottomCenter)) {
            Surface(color = Color(0xDD151922), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(enabled = index > 0, onClick = { index--; onIndex(index); reset() }) { Icon(Icons.Rounded.ChevronLeft, null, tint = if (index > 0) Color.White else Color.Gray) }
                    FilterChip(
                        selected = true,
                        onClick = { },
                        enabled = false,
                        label = { Text("Pixel", color = Color.White) },
                        leadingIcon = { Icon(Icons.Rounded.GridOn, null, tint = Color.White) },
                        colors = FilterChipDefaults.filterChipColors(
                            disabledSelectedContainerColor = Color(0xFF3A3D46),
                            disabledLabelColor = Color.White,
                            disabledLeadingIconColor = Color.White
                        )
                    )
                    Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    IconButton(onClick = { reset() }) { Icon(Icons.Rounded.FitScreen, null, tint = Color.White) }
                    IconButton(enabled = index < photos.lastIndex, onClick = { index++; onIndex(index); reset() }) { Icon(Icons.Rounded.ChevronRight, null, tint = if (index < photos.lastIndex) Color.White else Color.Gray) }
                }
            }
        }
    }
}

suspend fun loadPhotos(context: android.content.Context) = withContext(Dispatchers.IO) {
    val result = mutableListOf<LocalPhoto>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.DATE_ADDED
    )
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            result += LocalPhoto(
                id,
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                cursor.getString(nameColumn) ?: "Image",
                cursor.getInt(widthColumn),
                cursor.getInt(heightColumn),
                cursor.getLong(dateColumn)
            )
        }
    }
    result
}



suspend fun loadFolderPhotos(context: android.content.Context, treeUri: android.net.Uri) = withContext(Dispatchers.IO) {
    val result = mutableListOf<LocalPhoto>()
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    var syntheticId = -1L

    fun scan(documentId: String) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex) ?: "Image"
                val mime = cursor.getString(mimeIndex) ?: ""
                val date = cursor.getLong(dateIndex)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    scan(childId)
                } else if (mime.startsWith("image/")) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    try {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    } catch (_: Exception) { }
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        result += LocalPhoto(syntheticId--, uri, name, options.outWidth, options.outHeight, date)
                    }
                }
            }
        }
    }

    scan(rootId)
    result.sortedByDescending { it.date }
}