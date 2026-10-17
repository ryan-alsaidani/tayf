package com.tayf.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, DocumentEntity::class, PageEntity::class, PageFts::class],
    version = 1,
    exportSchema = false
)
abstract class TayfDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun ftsDao(): FtsDao

    companion object {
        const val DB_NAME = "tayf.db"

        fun build(context: Context): TayfDatabase =
            Room.databaseBuilder(context.applicationContext, TayfDatabase::class.java, DB_NAME).build()
    }
}
