package com.wallifyhd.app.data.repository

import com.wallifyhd.app.BuildConfig
import com.wallifyhd.app.data.local.FavoriteWallpaperDao
import com.wallifyhd.app.data.local.toEntity
import com.wallifyhd.app.data.local.toWallpaper
import com.wallifyhd.app.data.model.Wallpaper
import com.wallifyhd.app.data.model.WallpaperCategory
import com.wallifyhd.app.data.model.WallpaperPage
import com.wallifyhd.app.data.model.WallpaperSource
import com.wallifyhd.app.data.network.UnsplashApiService
import com.wallifyhd.app.data.network.UnsplashPhotoDto
import com.wallifyhd.app.data.network.WallhavenApiService
import com.wallifyhd.app.data.network.WallhavenWallpaperDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

interface WallpaperRepository {
    suspend fun getWallpapers(
        page: Int,
        pageSize: Int,
        category: WallpaperCategory,
        query: String
    ): WallpaperPage

    suspend fun getWallpaperById(wallpaperId: String): Wallpaper?

    fun observeFavorites(): Flow<List<Wallpaper>>

    fun observeFavoriteIds(): Flow<Set<String>>

    suspend fun toggleFavorite(wallpaper: Wallpaper): Boolean

    suspend fun updateLocalUri(wallpaperId: String, localUri: String)
}

class DefaultWallpaperRepository(
    private val wallhavenApi: WallhavenApiService,
    private val unsplashApi: UnsplashApiService,
    private val favoriteDao: FavoriteWallpaperDao
) : WallpaperRepository {

    private val memoryCache = ConcurrentHashMap<String, Wallpaper>()
    private val useUnsplash = BuildConfig.UNSPLASH_ACCESS_KEY.isNotBlank() &&
        BuildConfig.WALLHAVEN_API_KEY.isBlank()

    override suspend fun getWallpapers(
        page: Int,
        pageSize: Int,
        category: WallpaperCategory,
        query: String
    ): WallpaperPage {
        val result = if (useUnsplash) {
            fetchUnsplashPage(page = page, pageSize = pageSize, category = category, query = query)
        } else {
            fetchWallhavenPage(page = page, pageSize = pageSize, category = category, query = query)
        }

        val hydratedItems = hydrateLocalCopies(result.items)
        hydratedItems.forEach { memoryCache[it.id] = it }
        return result.copy(items = hydratedItems)
    }

    override suspend fun getWallpaperById(wallpaperId: String): Wallpaper? {
        memoryCache[wallpaperId]?.let { return hydrateLocalCopy(it) }

        favoriteDao.findByWallpaperId(wallpaperId)?.toWallpaper()?.let { favorite ->
            memoryCache[favorite.id] = favorite
            return favorite
        }

        val source = WallpaperSource.fromWallpaperId(wallpaperId)
        val remoteId = wallpaperId.substringAfter('_', missingDelimiterValue = wallpaperId)
        val wallpaper = when (source) {
            WallpaperSource.WALLHAVEN -> {
                wallhavenApi.getWallpaper(remoteId).data.toWallpaper(
                    selectedCategory = WallpaperCategory.ALL,
                    fallbackQuery = ""
                )
            }

            WallpaperSource.UNSPLASH -> {
                unsplashApi.getPhoto(remoteId).toWallpaper(selectedCategory = WallpaperCategory.ALL)
            }
        }

        val hydrated = hydrateLocalCopy(wallpaper)
        memoryCache[hydrated.id] = hydrated
        return hydrated
    }

    override fun observeFavorites(): Flow<List<Wallpaper>> =
        favoriteDao.observeAll().map { entities -> entities.map { it.toWallpaper() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeIds().map { ids -> ids.toSet() }

    override suspend fun toggleFavorite(wallpaper: Wallpaper): Boolean {
        val isFavorite = favoriteDao.isFavorite(wallpaper.id)
        val favoriteNow = if (isFavorite) {
            favoriteDao.deleteByWallpaperId(wallpaper.id)
            false
        } else {
            favoriteDao.upsert(wallpaper.toEntity())
            true
        }
        memoryCache[wallpaper.id] = if (favoriteNow) {
            hydrateLocalCopy(wallpaper)
        } else {
            wallpaper.copy(localUri = wallpaper.localUri)
        }
        return favoriteNow
    }

    override suspend fun updateLocalUri(wallpaperId: String, localUri: String) {
        favoriteDao.updateLocalUri(wallpaperId, localUri)
        memoryCache[wallpaperId]?.let { cached ->
            memoryCache[wallpaperId] = cached.copy(localUri = localUri)
        }
    }

    private suspend fun hydrateLocalCopy(wallpaper: Wallpaper): Wallpaper {
        val localCopy = favoriteDao.findByWallpaperId(wallpaper.id)?.localUri
        return if (localCopy.isNullOrBlank()) wallpaper else wallpaper.copy(localUri = localCopy)
    }

    private suspend fun hydrateLocalCopies(items: List<Wallpaper>): List<Wallpaper> {
        val hydrated = ArrayList<Wallpaper>(items.size)
        for (wallpaper in items) {
            hydrated += hydrateLocalCopy(wallpaper)
        }
        return hydrated
    }

    private suspend fun fetchWallhavenPage(
        page: Int,
        pageSize: Int,
        category: WallpaperCategory,
        query: String
    ): WallpaperPage {
        val effectiveQuery = buildString {
            if (category != WallpaperCategory.ALL) {
                append(category.wallhavenQuery)
            }
            if (query.isNotBlank()) {
                if (isNotEmpty()) append(' ')
                append(query.trim())
            }
        }.trim()

        val response = wallhavenApi.searchWallpapers(
            query = effectiveQuery.ifBlank { null },
            page = page,
            sorting = if (effectiveQuery.isBlank()) "toplist" else "relevance",
            topRange = if (effectiveQuery.isBlank()) "1M" else null
        )

        val items = response.data.take(pageSize).map { wallpaper ->
            wallpaper.toWallpaper(
                selectedCategory = category,
                fallbackQuery = effectiveQuery
            )
        }

        return WallpaperPage(
            items = items,
            hasMore = response.meta.currentPage < response.meta.lastPage && items.isNotEmpty()
        )
    }

    private suspend fun fetchUnsplashPage(
        page: Int,
        pageSize: Int,
        category: WallpaperCategory,
        query: String
    ): WallpaperPage {
        val effectiveQuery = when {
            query.isNotBlank() -> query.trim()
            category != WallpaperCategory.ALL -> category.unsplashQuery
            else -> ""
        }

        return if (effectiveQuery.isBlank()) {
            val items = unsplashApi.getPhotos(page = page, perPage = pageSize).map { photo ->
                photo.toWallpaper(selectedCategory = category)
            }

            WallpaperPage(
                items = items,
                hasMore = items.size >= pageSize
            )
        } else {
            val response = unsplashApi.searchPhotos(
                query = effectiveQuery,
                page = page,
                perPage = pageSize
            )

            WallpaperPage(
                items = response.results.map { photo ->
                    photo.toWallpaper(
                        selectedCategory = if (category == WallpaperCategory.ALL) {
                            WallpaperCategory.inferFromText(effectiveQuery)
                        } else {
                            category
                        }
                    )
                },
                hasMore = page < response.totalPages
            )
        }
    }
}

private fun WallhavenWallpaperDto.toWallpaper(
    selectedCategory: WallpaperCategory,
    fallbackQuery: String
): Wallpaper {
    val resolvedCategory = when {
        selectedCategory != WallpaperCategory.ALL -> selectedCategory
        fallbackQuery.isNotBlank() -> WallpaperCategory.inferFromText(fallbackQuery)
        tags.isNotEmpty() -> WallpaperCategory.inferFromText(tags.joinToString(" ") { it.name })
        !category.isNullOrBlank() -> WallpaperCategory.inferFromText(category)
        else -> WallpaperCategory.ALL
    }

    val tagNames = tags.map { it.name.trim() }.filter { it.isNotBlank() }.distinct()
    val title = when {
        tagNames.isNotEmpty() -> tagNames.take(2).joinToString(" ").replaceFirstChar { it.uppercase() }
        resolvedCategory != WallpaperCategory.ALL -> "${resolvedCategory.label} Select"
        else -> "Wallify Pick"
    }

    return Wallpaper(
        id = "${WallpaperSource.WALLHAVEN.idPrefix}_$id",
        remoteId = id,
        source = WallpaperSource.WALLHAVEN,
        title = title,
        description = tagNames.take(4).joinToString(" • ").ifBlank { "Curated from Wallhaven" },
        imageUrl = imagePath,
        thumbnailUrl = thumbs.large.ifBlank { thumbs.small.ifBlank { imagePath } },
        sourceUrl = source?.takeIf { it.isNotBlank() } ?: url,
        photographerName = uploader?.username?.ifBlank { "Wallhaven" } ?: "Wallhaven",
        photographerUsername = uploader?.username?.ifBlank { "wallhaven" } ?: "wallhaven",
        photographerProfileUrl = "https://wallhaven.cc/",
        width = width,
        height = height,
        dominantColor = colors.firstOrNull(),
        category = resolvedCategory,
        tags = tagNames
    )
}

private fun UnsplashPhotoDto.toWallpaper(selectedCategory: WallpaperCategory): Wallpaper {
    val resolvedCategory = when {
        selectedCategory != WallpaperCategory.ALL -> selectedCategory
        !description.isNullOrBlank() -> WallpaperCategory.inferFromText(description)
        !altDescription.isNullOrBlank() -> WallpaperCategory.inferFromText(altDescription)
        else -> WallpaperCategory.ALL
    }

    val title = description
        ?: altDescription
        ?: "${resolvedCategory.label.takeIf { it.isNotBlank() } ?: "Featured"} Scene"

    return Wallpaper(
        id = "${WallpaperSource.UNSPLASH.idPrefix}_$id",
        remoteId = id,
        source = WallpaperSource.UNSPLASH,
        title = title.replaceFirstChar { it.uppercase() },
        description = altDescription ?: description,
        imageUrl = urls.regular,
        thumbnailUrl = urls.small,
        sourceUrl = links.html,
        photographerName = user.name,
        photographerUsername = user.username,
        photographerProfileUrl = user.links.html,
        width = width,
        height = height,
        dominantColor = color,
        category = resolvedCategory,
        tags = tags.orEmpty().map { it.title }.filter { it.isNotBlank() }
    )
}
