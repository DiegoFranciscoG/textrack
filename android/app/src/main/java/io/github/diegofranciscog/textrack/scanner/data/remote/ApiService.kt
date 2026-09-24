package io.github.diegofranciscog.textrack.scanner.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Endpoints que usa la tablet de planta (rol SCANNER). */
interface ApiService {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    /** Síncrono: se invoca desde el Authenticator de OkHttp. */
    @POST("api/v1/auth/refresh")
    fun refresh(@Body request: RefreshRequest): Call<TokenResponse>

    @GET("api/v1/operators")
    suspend fun operators(): List<OperatorDto>

    @POST("api/v1/readings/batch")
    suspend fun syncBatch(@Body request: BatchScanRequest): BatchScanResponse
}

@Serializable
data class LoginRequest(val email: String, val password: String) {
    override fun toString(): String = "LoginRequest(email=$email, password=***)"
}

@Serializable
data class RefreshRequest(val refreshToken: String) {
    override fun toString(): String = "RefreshRequest(***)"
}

@Serializable
data class UserSummary(val id: Long, val email: String, val fullName: String, val role: String)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val refreshToken: String,
    val user: UserSummary,
) {
    override fun toString(): String = "TokenResponse(user=$user)"
}

@Serializable
data class OperatorDto(
    val id: Long,
    val code: String,
    val fullName: String,
    val lineCode: String? = null,
    val active: Boolean = true,
)

@Serializable
data class ScanRequestDto(
    val clientReadingId: String,
    val payload: String,
    val operatorCode: String,
    val scannedAt: String,
    val deviceId: String,
)

@Serializable
data class BatchScanRequest(val readings: List<ScanRequestDto>)

@Serializable
data class ScanResultDto(
    val clientReadingId: String,
    val status: String,
    val message: String? = null,
    val registeredBy: String? = null,
)

@Serializable
data class BatchScanResponse(
    val results: List<ScanResultDto>,
    val accepted: Int,
    val duplicates: Int,
    val rejected: Int,
)
