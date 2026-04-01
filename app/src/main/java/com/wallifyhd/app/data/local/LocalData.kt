package com.wallifyhd.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.wallifyhd.app.data.model.Wallpaper
import com.wallifyhd.app.data.model.WallpaperCategory
import com.wallifyhd.app.data.model.WallpaperSource
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_wallpapers")
data class FavoriteWallpaperEntity(
    @PrimaryKey
    @ColumnInfo(name = "wallpaper_id") val wallpaperId: String,
    @ColumnInfo(name = "remote_id") val remoteId: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "photographer_name") val photographerName: String,
    @ColumnInfo(name = "photographer_username") val photographerUsername: String?,
    @ColumnInfo(name = "photographer_profile_url") val photographerProfileUrl: String?,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "dominant_color") val dominantColor: String?,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "tags_csv") val tagsCsv: String,
    @ColumnInfo(name = "local_uri") val localUri: String?,
    @ColumnInfo(name = "favorited_at") val favoritedAt: Long
)

@Dao
interface FavoriteWallpaperDao {
    @Query("SELECT * FROM favorite_wallpapers ORDER BY favorited_at DESC")
    fun observeAll(): Flow<List<FavoriteWallpaperEntity>>

    @Query("SELECT wallpaper_id FROM favorite_wallpapers")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT * FROM favorite_wallpapers WHERE wallpaper_id = :wallpaperId LIMIT 1")
    suspend fun findByWallpaperId(wallpaperId: String): FavoriteWallpaperEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_wallpapers WHERE wallpaper_id = :wallpaperId)")
    suspend fun isFavorite(wallpaperId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteWallpaperEntity)

    @Query("DELETE FROM favorite_wallpapers WHERE wallpaper_id = :wallpaperId")
    suspend fun deleteByWallpaperId(wallpaperId: String)

    @Query("UPDATE favorite_wallpapers SET local_uri = :localUri WHERE wallpaper_id = :wallpaperId")
    suspend fun updateLocalUri(wallpaperId: String, localUri: String)
}

@Database(
    entities = [FavoriteWallpaperEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WallifyDatabase : RoomDatabase() {
    abstract fun favoriteWallpaperDao(): FavoriteWallpaperDao
}

fun Wallpaper.toEntity(): FavoriteWallpaperEntity = FavoriteWallpaperEntity(
    wallpaperId = id,
    remoteId = remoteId,
    source = source.name,
    title = title,
    description = description,
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    sourceUrl = sourceUrl,
    photographerName = photographerName,
    photographerUsername = photographerUsername,
    photographerProfileUrl = photographerProfileUrl,
    width = width,
    height = height,
    dominantColor = dominantColor,
    category = category.name,
    tagsCsv = tags.joinToString("|"),
    localUri = localUri,
    favoritedAt = System.currentTimeMillis()
)

fun FavoriteWallpaperEntity.toWallpaper(): Wallpaper = Wallpaper(
    id = wallpaperId,
    remoteId = remoteId,
    source = WallpaperSource.valueOf(source),
    title = title,
    description = description,
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    sourceUrl = sourceUrl,
    photographerName = photographerName,
    photographerUsername = photographerUsername,
    photographerProfileUrl = photographerProfileUrl,
    width = width,
    height = height,
    dominantColor = dominantColor,
    category = WallpaperCategory.valueOf(category),
    tags = tagsCsv.split("|").filter { it.isNotBlank() },
    localUri = localUri
)
