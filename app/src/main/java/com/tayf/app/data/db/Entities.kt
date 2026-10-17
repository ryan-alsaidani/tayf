package com.tayf.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isVault: Boolean,
    val createdAt: Long
)

@Entity(tableName = "documents", indices = [Index("folderId")])
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val folderId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean,
    val isVault: Boolean,
    val docType: String,
    val expiryDate: Long?
)

@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("documentId")]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val position: Int,
    val originalPath: String,
    val processedPath: String,
    val thumbPath: String,
    val tlX: Float, val tlY: Float, val trX: Float, val trY: Float,
    val brX: Float, val brY: Float, val blX: Float, val blY: Float,
    val filter: String,
    val brightness: Float,
    val contrast: Float,
    val rotation: Int,
    val ocrText: String?,
    val ocrBlocksJson: String?
)

/** Full-text index of OCR text; rowid equals the page id. */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "page_fts")
data class PageFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val ocrText: String
)
