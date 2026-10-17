package com.tayf.app.privacy

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.FileStore
import com.tayf.app.core.Outcome
import com.tayf.app.core.PageModel
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Keystore-backed file encryption used for vault files (.enc). */
internal object VaultCrypto {
    const val EXT = ".enc"

    private fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    private fun encrypted(context: Context, file: File): EncryptedFile =
        EncryptedFile.Builder(
            context.applicationContext, file, masterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

    fun encryptFile(context: Context, plain: File, target: File) {
        if (target.exists()) target.delete()
        encrypted(context, target).openFileOutput().use { out -> plain.inputStream().use { it.copyTo(out, 64 * 1024) } }
    }

    fun readBytes(context: Context, file: File): ByteArray =
        encrypted(context, file).openFileInput().use { it.readBytes() }

    fun decryptTo(context: Context, file: File, out: File) {
        encrypted(context, file).openFileInput().use { input -> out.outputStream().use { input.copyTo(it, 64 * 1024) } }
    }
}

class Vault(
    private val context: Context,
    private val fileStore: FileStore,
    private val pinManager: PinManager
) {
    private val prefs = context.applicationContext.getSharedPreferences("tayf_vault", Context.MODE_PRIVATE)
    @Volatile private var unlocked = false

    suspend fun isInitialized(): Boolean = withContext(AppDispatchers.io) { prefs.contains("hash") }

    suspend fun create(password: String): Outcome<Unit> = withContext(AppDispatchers.cpu) {
        if (password.length < 4) return@withContext Outcome.Fail("Password too short")
        val salt = Pbkdf2.newSalt()
        prefs.edit().putString("salt", Pbkdf2.encode(salt)).putString("hash", Pbkdf2.encode(Pbkdf2.hash(password, salt))).apply()
        unlocked = true
        Outcome.Ok(Unit)
    }

    suspend fun unlock(password: String): Boolean = withContext(AppDispatchers.cpu) {
        val salt = prefs.getString("salt", null) ?: return@withContext false
        val stored = prefs.getString("hash", null) ?: return@withContext false
        val ok = MessageDigest.isEqual(Pbkdf2.hash(password, Pbkdf2.decode(salt)), Pbkdf2.decode(stored))
        if (ok) unlocked = true
        ok
    }

    fun lock() { unlocked = false }

    fun isUnlocked(): Boolean = unlocked

    /** Moves the page files into the vault directory as encrypted files and returns the updated model. */
    suspend fun encryptPageFiles(page: PageModel): Outcome<PageModel> = withContext(AppDispatchers.io) {
        try {
            val original = moveToVault(page.originalPath)
            val processed = moveToVault(page.processedPath)
            val thumb = moveToVault(page.thumbPath)
            Outcome.Ok(page.copy(originalPath = original, processedPath = processed, thumbPath = thumb))
        } catch (e: Throwable) {
            Outcome.Fail(e.message ?: "Encryption failed", e)
        }
    }

    private fun moveToVault(path: String): String {
        if (path.endsWith(VaultCrypto.EXT)) return path
        val src = File(path)
        if (!src.exists()) return path
        val target = File(fileStore.vaultDir(), "${UUID.randomUUID()}${VaultCrypto.EXT}")
        VaultCrypto.encryptFile(context, src, target)
        src.delete()
        return target.absolutePath
    }

    /** Decrypts an encrypted vault file into a temporary file. */
    suspend fun decryptToTemp(path: String): Outcome<File> = withContext(AppDispatchers.io) {
        try {
            val src = File(path)
            if (!src.exists()) return@withContext Outcome.Fail("File not found")
            val out = fileStore.newTempFile("jpg")
            if (path.endsWith(VaultCrypto.EXT)) VaultCrypto.decryptTo(context, src, out) else src.copyTo(out, overwrite = true)
            Outcome.Ok(out)
        } catch (e: Throwable) {
            Outcome.Fail(e.message ?: "Decryption failed", e)
        }
    }
}
