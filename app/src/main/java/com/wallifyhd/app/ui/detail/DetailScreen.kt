package com.wallifyhd.app.ui.detail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wallifyhd.app.data.model.Wallpaper
import com.wallifyhd.app.data.repository.WallpaperRepository
import com.wallifyhd.app.ui.components.MessageCard
import com.wallifyhd.app.util.ImageDownloader
import com.wallifyhd.app.util.WallpaperScaleMode
import com.wallifyhd.app.util.WallpaperSetter
import com.wallifyhd.app.util.WallpaperTarget
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val wallpaper: Wallpaper? = null,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val scaleMode: WallpaperScaleMode = WallpaperScaleMode.CROP
)

class DetailViewModel(
    private val repository: WallpaperRepository,
    private val imageDownloader: ImageDownloader,
    private val wallpaperSetter: WallpaperSetter
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var currentWallpaperId: String? = null

    init {
        viewModelScope.launch {
            repository.observeFavoriteIds().collect { ids ->
                _uiState.value = _uiState.value.copy(
                    isFavorite = currentWallpaperId != null && currentWallpaperId in ids
                )
            }
        }
    }

    fun loadWallpaper(wallpaperId: String) {
        if (currentWallpaperId == wallpaperId && _uiState.value.wallpaper != null) return
        currentWallpaperId = wallpaperId
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            runCatching { repository.getWallpaperById(wallpaperId) }
                .onSuccess { wallpaper ->
                    _uiState.value = _uiState.value.copy(
                        wallpaper = wallpaper,
                        isLoading = false,
                        errorMessage = if (wallpaper == null) "Wallpaper not found." else null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to load wallpaper."
                    )
                }
        }
    }

    fun setScaleMode(mode: WallpaperScaleMode) {
        _uiState.value = _uiState.value.copy(scaleMode = mode)
    }

    fun toggleFavorite() {
        val wallpaper = _uiState.value.wallpaper ?: return
        viewModelScope.launch {
            repository.toggleFavorite(wallpaper)
        }
    }

    fun downloadWallpaper() {
        val wallpaper = _uiState.value.wallpaper ?: return
        runAction(successMessage = "Wallpaper saved to your gallery.") {
            val localUri = imageDownloader.download(wallpaper)
            repository.updateLocalUri(wallpaper.id, localUri)
            _uiState.value = _uiState.value.copy(
                wallpaper = wallpaper.copy(localUri = localUri)
            )
        }
    }

    fun applyWallpaper(target: WallpaperTarget) {
        val wallpaper = _uiState.value.wallpaper ?: return
        val scaleMode = _uiState.value.scaleMode
        val message = when (target) {
            WallpaperTarget.HOME -> "Wallpaper applied to the home screen."
            WallpaperTarget.LOCK -> "Wallpaper applied to the lock screen."
            WallpaperTarget.BOTH -> "Wallpaper applied to both screens."
        }
        runAction(successMessage = message) {
            wallpaperSetter.setWallpaper(
                wallpaper = wallpaper,
                target = target,
                scaleMode = scaleMode
            )
        }
    }

    private fun runAction(
        successMessage: String,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true)
            runCatching { action() }
                .onSuccess {
                    _events.send(successMessage)
                }
                .onFailure { error ->
                    _events.send(error.message ?: "Something went wrong.")
                }
            _uiState.value = _uiState.value.copy(isWorking = false)
        }
    }
}

@Composable
fun DetailScreen(
    wallpaperId: String,
    viewModelFactory: ViewModelProvider.Factory,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DetailViewModel = viewModel(
        key = wallpaperId,
        factory = viewModelFactory
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.downloadWallpaper()
        }
    }

    LaunchedEffect(wallpaperId) {
        viewModel.loadWallpaper(wallpaperId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.errorMessage != null || state.wallpaper == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MessageCard(
                        title = "Preview unavailable",
                        subtitle = state.errorMessage ?: "This wallpaper could not be loaded."
                    )
                }
            }

            else -> {
                val wallpaper = requireNotNull(state.wallpaper)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    PreviewHeader(
                        wallpaper = wallpaper,
                        isFavorite = state.isFavorite,
                        onBackClick = onBackClick,
                        onFavoriteClick = viewModel::toggleFavorite
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Text(
                            text = wallpaper.title,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${wallpaper.source.displayName} • ${wallpaper.photographerName} • ${wallpaper.resolutionLabel}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (wallpaper.tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                wallpaper.tags.take(6).forEach { tag ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(text = tag) }
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Wallpaper fit",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilterChip(
                                    selected = state.scaleMode == WallpaperScaleMode.CROP,
                                    onClick = { viewModel.setScaleMode(WallpaperScaleMode.CROP) },
                                    label = { Text(text = "Crop to fill") }
                                )
                                FilterChip(
                                    selected = state.scaleMode == WallpaperScaleMode.FIT,
                                    onClick = { viewModel.setScaleMode(WallpaperScaleMode.FIT) },
                                    label = { Text(text = "Fit with borders") }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val shouldRequestLegacyStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) != PackageManager.PERMISSION_GRANTED

                                if (shouldRequestLegacyStorage) {
                                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else {
                                    viewModel.downloadWallpaper()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isWorking,
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Download Image")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.applyWallpaper(WallpaperTarget.HOME) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isWorking,
                                contentPadding = PaddingValues(vertical = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhoneAndroid,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Home")
                            }

                            Button(
                                onClick = { viewModel.applyWallpaper(WallpaperTarget.LOCK) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isWorking,
                                contentPadding = PaddingValues(vertical = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Lock")
                            }
                        }

                        Button(
                            onClick = { viewModel.applyWallpaper(WallpaperTarget.BOTH) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isWorking,
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wallpaper,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Set on Both")
                        }

                        if (!wallpaper.localUri.isNullOrBlank()) {
                            MessageCard(
                                title = "Saved locally",
                                subtitle = "This wallpaper has already been downloaded and can be reused offline."
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
    onBackClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
    ) {
        AsyncImage(
            model = wallpaper.primaryImage,
            contentDescription = wallpaper.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x85000000),
                            Color.Transparent,
                            Color(0xD9000000)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0x66000000)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = Color(0x66000000)
            ) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (isFavorite) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF7B7B) else Color.White
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x70000000)
            ) {
                Text(
                    text = wallpaper.category.label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = wallpaper.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
    }
}
