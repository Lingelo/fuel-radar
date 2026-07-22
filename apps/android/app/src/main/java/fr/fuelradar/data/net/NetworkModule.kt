package fr.fuelradar.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import fr.fuelradar.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Builds the HTTP stack (KTD8). OkHttp disk cache + a network interceptor that
 * makes GitHub Pages responses cacheable, and an application interceptor that
 * serves stale cache when offline — the native equivalent of the web's
 * NetworkFirst runtime caching (apps/web/vite.config.ts). Supports R9.
 */
class NetworkModule(context: Context) {

    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val cache = Cache(File(appContext.cacheDir, "http-cache"), MAX_CACHE_BYTES)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cache(cache)
        .addInterceptor(OfflineFallbackInterceptor(appContext))
        .addNetworkInterceptor(CacheControlInterceptor())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val converter = json.asConverterFactory("application/json".toMediaType())

    val fuelApi: FuelApi = Retrofit.Builder()
        .baseUrl(BuildConfig.DATA_BASE_URL)
        .client(client)
        .addConverterFactory(converter)
        .build()
        .create(FuelApi::class.java)

    val geocodeApi: GeocodeApi = Retrofit.Builder()
        // baseUrl is required but overridden by @Url on every call.
        .baseUrl("https://api-adresse.data.gouv.fr/")
        .client(client)
        .addConverterFactory(converter)
        .build()
        .create(GeocodeApi::class.java)

    private companion object {
        const val MAX_CACHE_BYTES = 50L * 1024 * 1024
    }
}

/** Rewrites responses so they are cacheable even when the origin headers are weak. */
private class CacheControlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return response.newBuilder()
            .header("Cache-Control", "public, max-age=$MAX_AGE_SECONDS")
            .removeHeader("Pragma")
            .build()
    }

    private companion object {
        const val MAX_AGE_SECONDS = 600
    }
}

/** When offline, force reads from cache (stale allowed) instead of failing. */
private class OfflineFallbackInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (!isOnline()) {
            request = request.newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(MAX_STALE_DAYS, TimeUnit.DAYS)
                        .build(),
                )
                .build()
        }
        return chain.proceed(request)
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val MAX_STALE_DAYS = 7
    }
}
