package io.github.diegofranciscog.textrack.scanner.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Tokens de sesión. El refresh token se guarda cifrado con AES-256-GCM usando una clave no exportable del
 * Android Keystore; el JWT de acceso (15 min) solo vive en memoria.
 */
class TokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("textrack.secure", Context.MODE_PRIVATE)

    @Volatile
    var accessToken: String? = null
        private set

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: ("tablet-" + UUID.randomUUID().toString().take(8)).also {
            prefs.edit().putString(KEY_DEVICE, it).apply()
        }

    val userName: String?
        get() = prefs.getString(KEY_USER, null)

    fun hasSession(): Boolean = prefs.contains(KEY_REFRESH)

    fun refreshToken(): String? = prefs.getString(KEY_REFRESH, null)?.let(::decrypt)

    fun save(accessToken: String, refreshToken: String, userName: String) {
        this.accessToken = accessToken
        prefs.edit().putString(KEY_REFRESH, encrypt(refreshToken)).putString(KEY_USER, userName).apply()
    }

    fun clear() {
        accessToken = null
        prefs.edit().remove(KEY_REFRESH).remove(KEY_USER).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "textrack-session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val KEY_REFRESH = "refresh"
        const val KEY_USER = "user"
        const val KEY_DEVICE = "device"
    }
}
