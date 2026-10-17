package com.tayf.app.privacy

import android.content.Context
import android.util.Base64
import com.tayf.app.core.AppDispatchers
import com.tayf.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted PBKDF2 helper shared by PIN and vault password. */
internal object Pbkdf2 {
    private const val ITERATIONS = 120_000

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun encode(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun decode(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}

class PinManager(context: Context, private val settings: SettingsRepository) {
    private val prefs = context.applicationContext.getSharedPreferences("tayf_pin", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var unlocked = false

    init {
        // mirror the enabled flag so isLockedNow() can answer synchronously on cold start
        scope.launch { settings.pinEnabled.collect { prefs.edit().putBoolean("enabled", it).apply() } }
    }

    suspend fun isPinSet(): Boolean = withContext(AppDispatchers.io) { prefs.contains("hash") }

    suspend fun setPin(pin: String) {
        withContext(AppDispatchers.cpu) {
            val salt = Pbkdf2.newSalt()
            val hash = Pbkdf2.hash(pin, salt)
            prefs.edit().putString("salt", Pbkdf2.encode(salt)).putString("hash", Pbkdf2.encode(hash))
                .putBoolean("enabled", true).apply()
        }
        settings.setPinEnabled(true)
        unlocked = true
    }

    suspend fun verify(pin: String): Boolean = withContext(AppDispatchers.cpu) {
        val salt = prefs.getString("salt", null) ?: return@withContext false
        val stored = prefs.getString("hash", null) ?: return@withContext false
        MessageDigest.isEqual(Pbkdf2.hash(pin, Pbkdf2.decode(salt)), Pbkdf2.decode(stored))
    }

    /** Removes the PIN when [pin] is correct. */
    suspend fun clearPin(pin: String): Boolean {
        if (!verify(pin)) return false
        prefs.edit().remove("salt").remove("hash").putBoolean("enabled", false).apply()
        settings.setPinEnabled(false)
        return true
    }

    fun isLockedNow(): Boolean = prefs.contains("hash") && prefs.getBoolean("enabled", true) && !unlocked

    fun markUnlocked() { unlocked = true }

    fun lock() { unlocked = false }
}
