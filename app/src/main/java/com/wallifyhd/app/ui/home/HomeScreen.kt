package com.wallifyhd.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wallifyhd.app.BuildConfig
import com.wallifyhd.app.data.model.Wallpaper
import com.wallifyhd.app.data.model.WallpaperCategory
import com.wallifyhd.app.data.repository.WallpaperRepository
import com.wallifyhd.app.ui.components.CategoryFilterRow
import com.wallifyhd.app.ui.components.MessageCard
import com.wallifyhd.app.ui.components.WallifySearchField
import com.wallifyhd.app.ui.components.WallpaperCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val PageSize = 24

data class HomeUiState(
    val wallpapers: List<Wallpaper> = emptyList(),
    val selectedCategory: WallpaperCategory = WallpaperCategory.ALL,
    val searchQuery: String = "",
    val favoriteIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: WallpaperRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private var currentPage = 1
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeFavoriteIds().collect { ids ->
                _uiState.value = _uiState.value.copy(favoriteIds = ids)
            }
        }
        refresh()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(450)
            refresh()
        }
    }

    fun onCategorySelected(category: WallpaperCategory) {
        if (category == _uiState.value.selectedCategory) return
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        refresh()
    }

    fun refresh() {
        currentPage = 1
        loadPage(reset = true)
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        loadPage(reset = false)
    }

    fun toggleFavorite(wallpaper: Wallpaper) {
        viewModelScope.launch {
            repository.toggleFavorite(wallpaper)
        }
    }

    private fun loadPage(reset: Boolean) {
        val state = _uiState.value
        if (state.isLoading && !reset) return

        _uiState.value = state.copy(
            isLoading = reset,
            isLoadingMore = !reset,
            errorMessage = null,
            wallpapers = if (reset) emptyList() else state.wallpapers,
            hasMore = if (reset) true else state.hasMore
        )

        viewModelScope.launch {
            runCatching {
                repository.getWallpapers(
                    page = currentPage,
                    pageSize = PageSize,
                    category = _uiState.value.selectedCategory,
                    query = _uiState.value.searchQuery
                )
            }.onSuccess { page ->
                val updatedItems = if (reset) {
                    page.items
                } else {
                    _uiState.value.wallpapers + page.items
                }

                _uiState.value = _uiState.value.copy(
                    wallpapers = updatedItems.distinctBy { it.id },
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = page.hasMore,
                    errorMessage = null
                )
                if (page.items.isNotEmpty()) {
                    currentPage += 1
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = error.message ?: "Something went wrong while loading wallpapers."
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onWallpaperSelected: (Wallpaper) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, state.wallpapers.size, state.hasMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val threshold = (state.wallpapers.lastIndex - 5).coerceAtLeast(0)
                if (lastVisibleIndex >= threshold) {
                    viewModel.loadNextPage()
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        modifier = modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            HeroHeader(
                searchQuery = state.searchQuery,
                selectedCategory = state.selectedCategory,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onCategorySelected = viewModel::onCategorySelected
            )
        }

        if (state.isLoading && state.wallpapers.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (!state.isLoading && state.errorMessage != null && state.wallpapers.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MessageCard(
                    title = "We hit a snag",
                    subtitle = state.errorMessage ?: "Unable to load wallpapers."
                )
            }
        }

        if (!state.isLoading && state.wallpapers.isEmpty() && state.errorMessage == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MessageCard(
                    title = "Nothing matched this search",
                    subtitle = "Try another category, a broader keyword, or clear the search field."
                )
            }
        }

        items(
            count = state.wallpapers.size,
            key = { index -> state.wallpapers[index].id }
        ) { index ->
            val wallpaper = state.wallpapers[index]
            WallpaperCard(
                wallpaper = wallpaper,
                isFavorite = wallpaper.id in state.favoriteIds,
                onFavoriteClick = viewModel::toggleFavorite,
                onClick = onWallpaperSelected
            )
        }

        if (state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(
    searchQuery: String,
    selectedCategory: WallpaperCategory,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (WallpaperCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
                    )
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
            )
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Wallify HD",
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = "Discover sharp, immersive wallpapers and set them in a tap.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (BuildConfig.WALLHAVEN_API_KEY.isNotBlank()) {
            Text(
                text = "Live source: authenticated Wallhaven feed.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        } else if (BuildConfig.UNSPLASH_ACCESS_KEY.isBlank()) {
            Text(
                text = "Live source: Wallhaven fallback. Add an Unsplash key in Gradle to switch.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        WallifySearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange
        )
        CategoryFilterRow(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
    }
}
