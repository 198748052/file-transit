package com.filetransit.app.data

import com.filetransit.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    fun create(session: SessionStore, onUnauthorized: () -> Unit): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(BaseUrlInterceptor(session))
            .addInterceptor(AuthInterceptor(session))
            .addInterceptor(UnauthorizedInterceptor(onUnauthorized))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}

private class BaseUrlInterceptor(private val session: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val server = session.server.value
        if (server.isBlank()) return chain.proceed(request)

        val path = request.url.encodedPath.trimStart('/')
        val query = request.url.encodedQuery?.let { "?$it" }.orEmpty()
        val target = "${server.trimEnd('/')}/$path$query".toHttpUrlOrNull()
            ?: return chain.proceed(request)

        return chain.proceed(request.newBuilder().url(target).build())
    }
}

private class AuthInterceptor(private val session: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.token.value
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(request)
    }
}

private class UnauthorizedInterceptor(private val onUnauthorized: () -> Unit) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401 && request.header("Authorization") != null) {
            onUnauthorized()
        }
        return response
    }
}
