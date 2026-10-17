package com.tayf.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.FileStore
import com.tayf.app.core.Outcome
import com.tayf.app.data.db.TayfDatabase
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Backup = one file: header + (optionally AES-encrypted) zip of the database and the scan images. */
class BackupManager(
    private val context: Context,
    private val db: TayfDatabase,
    private val fileStore: FileStore
) {
    private companion object {
        val MAGIC = "TAYFBK01".toByteArray(Charsets.US_ASCII)
        val OK_MARK = "TAYF-OK1".toByteArray(Charsets.US_ASCII)
        const val ITERATIONS = 120_000
        const val DB_ENTRY = "tayf.db"
        const val MANIFEST = "manifest.txt"
    }

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = f.generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)).encoded
        return SecretKeySpec(key, "AES")
    }

    /** Returns the number of bytes written. */
    suspend fun createBackup(target: Uri, password: String?): Outcome<Long> = withContext(AppDispatchers.io) {
        try {
            runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close() }
            val dbFile = context.getDatabasePath(TayfDatabase.DB_NAME)
            val raw = context.contentResolver.openOutputStream(target, "w")
                ?: return@withContext Outcome.Fail("Cannot open target")
            val counting = CountingOutputStream(BufferedOutputStream(raw))
            counting.use { sink ->
                sink.write(MAGIC)
                var payload: OutputStream = sink
                if (!password.isNullOrEmpty()) {
                    val rnd = SecureRandom()
                    val salt = ByteArray(16).also { rnd.nextBytes(it) }
                    val iv = ByteArray(16).also { rnd.nextBytes(it) }
                    sink.write(1)
                    sink.write(salt)
                    sink.write(iv)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), IvParameterSpec(iv))
                    payload = CipherOutputStream(sink, cipher)
                } else {
                    sink.write(0)
                }
                payload.write(OK_MARK)
                val zip = ZipOutputStream(payload)
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write("filesDir=${context.filesDir.absolutePath}\n".toByteArray())
                zip.closeEntry()
                if (dbFile.exists()) addFile(zip, DB_ENTRY, dbFile)
                for (sub in listOf("scans", "vault")) {
                    val root = File(context.filesDir, sub)
                    if (!root.exists()) continue
                    root.walkTopDown().filter { it.isFile }.forEach { f ->
                        val rel = f.relativeTo(context.filesDir).path.replace(File.separatorChar, '/')
                        addFile(zip, "files/$rel", f)
                    }
                }
                zip.finish()
                zip.flush()
                payload.close()
            }
            Outcome.Ok(counting.count)
        } catch (e: Throwable) {
            Outcome.Fail(e.message ?: "Backup failed", e)
        }
    }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip, 64 * 1024) }
        zip.closeEntry()
    }

    /** After success the app must be restarted (the database file was replaced). */
    suspend fun restoreBackup(source: Uri, password: String?): Outcome<Unit> = withContext(AppDispatchers.io) {
        val staging = File(context.cacheDir, "restore_${System.currentTimeMillis()}")
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: return@withContext Outcome.Fail("Cannot open backup")
            input.use { raw ->
                val header = ByteArray(MAGIC.size)
                readFully(raw, header)
                if (!header.contentEquals(MAGIC)) return@withContext Outcome.Fail("Not a Tayf backup")
                val flag = raw.read()
                var payload: InputStream = raw
                if (flag == 1) {
                    if (password.isNullOrEmpty()) return@withContext Outcome.Fail("Password required")
                    val salt = ByteArray(16); readFully(raw, salt)
                    val iv = ByteArray(16); readFully(raw, iv)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), IvParameterSpec(iv))
                    payload = CipherInputStream(raw, cipher)
                }
                val mark = ByteArray(OK_MARK.size)
                readFully(payload, mark)
                if (!mark.contentEquals(OK_MARK)) return@withContext Outcome.Fail("Wrong password or corrupt backup")
                staging.mkdirs()
                val zin = ZipInputStream(payload)
                var entry = zin.nextEntry
                while (entry != null) {
                    val out = File(staging, entry.name).canonicalFile
                    if (!out.path.startsWith(staging.canonicalPath)) throw SecurityException("Bad entry")
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zin.copyTo(it, 64 * 1024) }
                    }
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
            val restoredDb = File(staging, DB_ENTRY)
            if (!restoredDb.exists()) return@withContext Outcome.Fail("Backup has no database")
            val oldDir = File(staging, MANIFEST).takeIf { it.exists() }?.readLines()
                ?.firstOrNull { it.startsWith("filesDir=") }?.removePrefix("filesDir=")
            val newDir = context.filesDir.absolutePath
            if (!oldDir.isNullOrEmpty() && oldDir != newDir) rewritePaths(restoredDb, oldDir, newDir)

            db.close()
            val dbTarget = context.getDatabasePath(TayfDatabase.DB_NAME)
            File(dbTarget.path + "-wal").delete()
            File(dbTarget.path + "-shm").delete()
            restoredDb.copyTo(dbTarget, overwrite = true)

            for (sub in listOf("scans", "vault")) {
                File(context.filesDir, sub).deleteRecursively()
                val src = File(staging, "files/$sub")
                if (src.exists()) src.copyRecursively(File(context.filesDir, sub), overwrite = true)
            }
            Outcome.Ok(Unit)
        } catch (e: Throwable) {
            Outcome.Fail(e.message ?: "Restore failed", e)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw java.io.EOFException("Unexpected end of backup")
            off += n
        }
    }

    private fun rewritePaths(dbFile: File, oldDir: String, newDir: String) {
        val sql = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            for (col in listOf("originalPath", "processedPath", "thumbPath")) {
                sql.execSQL("UPDATE pages SET $col = REPLACE($col, ?, ?)", arrayOf<Any>(oldDir, newDir))
            }
        } finally {
            sql.close()
        }
    }
}
