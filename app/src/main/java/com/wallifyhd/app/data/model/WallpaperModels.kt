package com.wallifyhd.app.data.model

enum class WallpaperSource(val idPrefix: String, val displayName: String) {
    WALLHAVEN("wallhaven", "Wallhaven"),
    UNSPLASH("unsplash", "Unsplash");

    companion object {
        fun fromWallpaperId(wallpaperId: String): WallpaperSource =
            entries.firstOrNull { wallpaperId.startsWith("${it.idPrefix}_") } ?: WALLHAVEN
    }
}

enum class WallpaperCategory(
    val label: String,
    val wallhavenQuery: String,
    val unsplashQuery: String
) {
    ALL("All", "", ""),
    NATURE("Nature", "nature landscape mountains forest lake", "nature"),
    CARS("Cars", "cars automotive supercar motorsport", "cars"),
    ABSTRACT("Abstract", "abstract digital art geometric gradient", "abstract"),
    ANIMALS("Animals", "animals wildlife birds cats dogs", "animals"),
    TECHNOLOGY("Technology", "technology cyberpunk futuristic circuit sci-fi", "technology");

    companion object {
        val selectable = listOf(ALL, NATURE, CARS, ABSTRACT, ANIMALS, TECHNOLOGY)

        fun inferFromText(text: String): WallpaperCategory {
            val normalized = text.lowercase()
            return selectable.firstOrNull { category ->
                category != ALL && normalized.contains(category.label.lowercase())
            } ?: ALL
        }
    }
}

data class Wallpaper(
    val id: String,
    val remoteId: String,
    val source: WallpaperSource,
    val title: String,
    val description: String?,
    val imageUrl: String,
    val thumbnailUrl: String,
    val sourceUrl: String,
    val photographerName: String,
    val photographerUsername: String?,
    val photographerProfileUrl: String?,
    val width: Int,
    val height: Int,
    val dominantColor: String?,
    val category: WallpaperCategory,
    val tags: List<String> = emptyList(),
    val localUri: String? = null
) {
    val primaryImage: String
        get() = localUri ?: imageUrl

    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width} x ${height}" else "HD"
}

data class WallpaperPage(
    val items: List<Wallpaper>,
    val hasMore: Boolean
)

