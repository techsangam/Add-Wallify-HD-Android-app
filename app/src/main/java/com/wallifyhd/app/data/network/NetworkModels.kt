package com.wallifyhd.app.data.network

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class WallhavenSearchResponse(
    val data: List<WallhavenWallpaperDto> = emptyList(),
    val meta: WallhavenMetaDto = WallhavenMetaDto()
)

data class WallhavenDetailResponse(
    val data: WallhavenWallpaperDto
)

data class WallhavenWallpaperDto(
    val id: String,
    val url: String,
    @Json(name = "short_url") val shortUrl: String? = null,
    val source: String? = null,
    @Json(name = "path") val imagePath: String,
    @Json(name = "dimension_x") val width: Int = 0,
    @Json(name = "dimension_y") val height: Int = 0,
    val resolution: String? = null,
    @Json(name = "file_type") val fileType: String? = null,
    val uploader: WallhavenUploaderDto? = null,
    val category: String? = null,
    val colors: List<String> = emptyList(),
    val thumbs: WallhavenThumbsDto = WallhavenThumbsDto(),
    val tags: List<WallhavenTagDto> = emptyList()
)

data class WallhavenUploaderDto(
    val username: String = ""
)

data class WallhavenThumbsDto(
    val large: String = "",
    val original: String = "",
    val small: String = ""
)

data class WallhavenTagDto(
    val name: String = ""
)

data class WallhavenMetaDto(
    @Json(name = "current_page") val currentPage: Int = 1,
    @Json(name = "last_page") val lastPage: Int = 1,
    @Json(name = "per_page") val perPage: Int = 24,
    val seed: String? = null
)

interface WallhavenApiService {
    @GET("search")
    suspend fun searchWallpapers(
        @Query("q") query: String? = null,
        @Query("page") page: Int,
        @Query("categories") categories: String = "100",
        @Query("purity") purity: String = "100",
        @Query("sorting") sorting: String = "toplist",
        @Query("order") order: String = "desc",
        @Query("topRange") topRange: String? = "1M",
        @Query("atleast") atleast: String = "1080x1920"
    ): WallhavenSearchResponse

    @GET("w/{id}")
    suspend fun getWallpaper(
        @Path("id") id: String
    ): WallhavenDetailResponse
}

data class UnsplashSearchResponse(
    val results: List<UnsplashPhotoDto> = emptyList(),
    @Json(name = "total_pages") val totalPages: Int = 1
)

data class UnsplashPhotoDto(
    val id: String,
    val description: String? = null,
    @Json(name = "alt_description") val altDescription: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val color: String? = null,
    val urls: UnsplashUrlsDto,
    val links: UnsplashLinksDto,
    val user: UnsplashUserDto,
    val tags: List<UnsplashTagDto>? = emptyList()
)

data class UnsplashUrlsDto(
    val small: String,
    val regular: String
)

data class UnsplashLinksDto(
    val html: String,
    val download: String? = null,
    @Json(name = "download_location") val downloadLocation: String? = null
)

data class UnsplashUserDto(
    val name: String,
    val username: String,
    val links: UnsplashUserLinksDto
)

data class UnsplashUserLinksDto(
    val html: String
)

data class UnsplashTagDto(
    val title: String
)

interface UnsplashApiService {
    @GET("photos")
    suspend fun getPhotos(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("order_by") orderBy: String = "popular",
        @Query("orientation") orientation: String = "portrait"
    ): List<UnsplashPhotoDto>

    @GET("search/photos")
    suspend fun searchPhotos(
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("order_by") orderBy: String = "relevant",
        @Query("orientation") orientation: String = "portrait"
    ): UnsplashSearchResponse

    @GET("photos/{id}")
    suspend fun getPhoto(
        @Path("id") id: String
    ): UnsplashPhotoDto
}
