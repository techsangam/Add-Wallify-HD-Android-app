package com.wallifyhd.app.core

import android.content.Context
import androidx.room.Room
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import coil.ImageLoader
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.wallifyhd.app.BuildConfig
import com.wallifyhd.app.data.local.WallifyDatabase
import com.wallifyhd.app.data.network.UnsplashApiService
import com.wallifyhd.app.data.network.WallhavenApiService
import com.wallifyhd.app.data.repository.DefaultWallpaperRepository
import com.wallifyhd.app.data.repository.WallpaperRepository
import com.wallifyhd.app.ui.detail.DetailViewModel
import com.wallifyhd.app.ui.favorites.FavoritesViewModel
import com.wallifyhd.app.ui.home.HomeViewModel
import com.wallifyhd.app.util.ImageDownloader
import com.wallifyhd.app.util.WallpaperSetter
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val baseOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val wallhavenInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val urlBuilder = originalRequest.url.newBuilder()
        val requestBuilder = originalRequest.newBuilder()

        if (BuildConfig.WALLHAVEN_API_KEY.isNotBlank()) {
            urlBuilder.addQueryParameter("apikey", BuildConfig.WALLHAVEN_API_KEY)
            requestBuilder.header("X-API-Key", BuildConfig.WALLHAVEN_API_KEY)
        }

        chain.proceed(
            requestBuilder
                .url(urlBuilder.build())
                .build()
        )
    }

    private val unsplashInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Accept-Version", "v1")
            .apply {
                if (BuildConfig.UNSPLASH_ACCESS_KEY.isNotBlank()) {
                    header("Authorization", "Client-ID ${BuildConfig.UNSPLASH_ACCESS_KEY}")
                }
            }
            .build()
        chain.proceed(request)
    }

    private val unsplashOkHttpClient = baseOkHttpClient.newBuilder()
        .addInterceptor(unsplashInterceptor)
        .build()

    private val wallhavenOkHttpClient = baseOkHttpClient.newBuilder()
        .addInterceptor(wallhavenInterceptor)
        .build()

    private val wallhavenRetrofit = Retrofit.Builder()
        .baseUrl("https://wallhaven.cc/api/v1/")
        .client(wallhavenOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val unsplashRetrofit = Retrofit.Builder()
        .baseUrl("https://api.unsplash.com/")
        .client(unsplashOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val database = Room.databaseBuilder(
        appContext,
        WallifyDatabase::class.java,
        "wallify_hd.db"
    ).build()

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .crossfade(true)
        .build()

    val imageDownloader = ImageDownloader(
        context = appContext,
        okHttpClient = baseOkHttpClient
    )

    val wallpaperSetter = WallpaperSetter(
        context = appContext,
        imageLoader = imageLoader
    )

    val wallpaperRepository: WallpaperRepository = DefaultWallpaperRepository(
        wallhavenApi = wallhavenRetrofit.create(WallhavenApiService::class.java),
        unsplashApi = unsplashRetrofit.create(UnsplashApiService::class.java),
        favoriteDao = database.favoriteWallpaperDao()
    )
}

class WallifyViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
            HomeViewModel(container.wallpaperRepository) as T
        }

        modelClass.isAssignableFrom(FavoritesViewModel::class.java) -> {
            FavoritesViewModel(container.wallpaperRepository) as T
        }

        modelClass.isAssignableFrom(DetailViewModel::class.java) -> {
            DetailViewModel(
                repository = container.wallpaperRepository,
                imageDownloader = container.imageDownloader,
                wallpaperSetter = container.wallpaperSetter
            ) as T
        }

        else -> error("Unknown ViewModel class: ${modelClass.name}")
    }
}
