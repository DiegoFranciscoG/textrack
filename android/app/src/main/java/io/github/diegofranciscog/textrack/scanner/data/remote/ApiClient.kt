package io.github.diegofranciscog.textrack.scanner.data.remote

import io.github.diegofranciscog.textrack.scanner.data.security.TokenStore
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Construye el cliente HTTP: JWT en cada llamada y renovación automática con el refresh token ante un 401. */
object ApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    fun create(baseUrl: String, tokens: TokenStore): ApiService {
        // Cliente sin autenticador para renovar el token (evita recursión).
        val refreshApi = retrofit(baseUrl, baseClient().build())
        val client = baseClient()
            .addInterceptor(BearerInterceptor(tokens))
            .authenticator(RefreshAuthenticator(tokens, refreshApi))
            .build()
        return retrofit(baseUrl, client)
    }

    private fun baseClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)

    private fun retrofit(baseUrl: String, client: OkHttpClient): ApiService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)

    private class BearerInterceptor(private val tokens: TokenStore) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val token = tokens.accessToken
            if (token == null || request.url.encodedPath.contains("/api/v1/auth/")) {
                return chain.proceed(request)
            }
            return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        }
    }

    private class RefreshAuthenticator(
        private val tokens: TokenStore,
        private val refreshApi: ApiService,
    ) : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.request.url.encodedPath.contains("/api/v1/auth/") || responseCount(response) >= 2) {
                return null
            }
            synchronized(this) {
                val current = tokens.accessToken
                val sentWith = response.request.header("Authorization")?.removePrefix("Bearer ")
                // Otro hilo ya renovó el token mientras esperábamos.
                if (current != null && current != sentWith) {
                    return response.request.newBuilder().header("Authorization", "Bearer $current").build()
                }
                val refresh = tokens.refreshToken() ?: return null
                val renewed = runCatching { refreshApi.refresh(RefreshRequest(refresh)).execute() }.getOrNull()
                val body = renewed?.body()
                if (renewed?.isSuccessful != true || body == null) {
                    tokens.clear()
                    return null
                }
                tokens.save(body.accessToken, body.refreshToken, body.user.fullName)
                return response.request.newBuilder().header("Authorization", "Bearer ${body.accessToken}").build()
            }
        }

        private fun responseCount(response: Response): Int {
            var count = 1
            var prior = response.priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }
    }
}
