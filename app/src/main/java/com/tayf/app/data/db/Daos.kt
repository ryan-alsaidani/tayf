package com.tayf.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Query projection: a document plus its page count. */
data class DocumentRow(
    val id: Long,
    val name: String,
    val folderId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean,
    val isVault: Boolean,
    val docType: String,
    val expiryDate: Long?,
    val pageCount: Int
)

private const val DOC_SELECT =
    "SELECT d.id AS id, d.name AS name, d.folderId AS folderId, d.createdAt AS createdAt, d.updatedAt AS updatedAt, " +
        "d.isFavorite AS isFavorite, d.isVault AS isVault, d.docType AS docType, d.expiryDate AS expiryDate, " +
        "(SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount FROM documents d "

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: Long): FolderEntity?

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DocumentDao {
    @Query(DOC_SELECT + "WHERE d.isVault = 0 AND (:fav = 0 OR d.isFavorite = 1)")
    fun observeAll(fav: Int): Flow<List<DocumentRow>>

    @Query(DOC_SELECT + "WHERE d.folderId = :folderId AND (:fav = 0 OR d.isFavorite = 1)")
    fun observeInFolder(folderId: Long, fav: Int): Flow<List<DocumentRow>>

    @Query(DOC_SELECT + "WHERE d.id = :id")
    fun observeOne(id: Long): Flow<DocumentRow?>

    @Query(DOC_SELECT + "WHERE d.id = :id")
    suspend fun getRow(id: Long): DocumentRow?

    @Query(DOC_SELECT + "WHERE d.id IN (:ids)")
    suspend fun getRows(ids: List<Long>): List<DocumentRow>

    @Query(DOC_SELECT + "WHERE d.expiryDate IS NOT NULL AND d.expiryDate <= :limit AND d.isVault = 0 ORDER BY d.expiryDate ASC")
    suspend fun listExpiring(limit: Long): List<DocumentRow>

    @Insert
    suspend fun insert(doc: DocumentEntity): Long

    @Query("UPDATE documents SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE documents SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE documents SET folderId = :folderId, isVault = :isVault, updatedAt = :now WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?, isVault: Boolean, now: Long)

    @Query("UPDATE documents SET docType = :type WHERE id = :id")
    suspend fun setDocType(id: Long, type: String)

    @Query("UPDATE documents SET expiryDate = :expiry WHERE id = :id")
    suspend fun setExpiry(id: Long, expiry: Long?)

    @Query("UPDATE documents SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY position ASC")
    fun observeByDocument(docId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY position ASC")
    suspend fun getByDocument(docId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getById(id: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PageEntity>

    @Insert
    suspend fun insert(page: PageEntity): Long

    @Update
    suspend fun update(page: PageEntity)

    @Query("DELETE FROM pages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT MAX(position) FROM pages WHERE documentId = :docId")
    suspend fun maxPosition(docId: Long): Int?

    @Query("UPDATE pages SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("UPDATE pages SET documentId = :docId, position = :position WHERE id = :id")
    suspend fun moveToDocument(id: Long, docId: Long, position: Int)

    @Query("UPDATE pages SET ocrText = :text, ocrBlocksJson = :json WHERE id = :id")
    suspend fun setOcr(id: Long, text: String?, json: String?)

    @Query("SELECT * FROM pages")
    suspend fun getAll(): List<PageEntity>
}

@Dao
interface FtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fts: PageFts)

    @Query("DELETE FROM page_fts WHERE rowid IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT rowid FROM page_fts WHERE page_fts MATCH :match LIMIT 300")
    suspend fun search(match: String): List<Long>
}
