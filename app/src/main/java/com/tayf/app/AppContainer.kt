package com.tayf.app

import android.content.Context
import com.tayf.app.core.FileStore
import com.tayf.app.data.BackupManager
import com.tayf.app.data.DocumentRepository
import com.tayf.app.data.SettingsRepository
import com.tayf.app.data.db.TayfDatabase
import com.tayf.app.ocr.LanguagePacks
import com.tayf.app.ocr.OcrEngine
import com.tayf.app.ocr.SearchIndex
import com.tayf.app.ocr.TranslationEngine
import com.tayf.app.privacy.PinManager
import com.tayf.app.privacy.Vault

/** Manual dependency container (no DI framework). */
class AppContainer(val context: Context) {
    val fileStore: FileStore = FileStore(context)
    val database: TayfDatabase = TayfDatabase.build(context)
    val documents: DocumentRepository = DocumentRepository(database, fileStore)
    val settings: SettingsRepository = SettingsRepository(context)
    val backup: BackupManager = BackupManager(context, database, fileStore)
    val ocr: OcrEngine = OcrEngine(context)
    val searchIndex: SearchIndex = SearchIndex(database)
    val languagePacks: LanguagePacks = LanguagePacks(context, fileStore)
    val translator: TranslationEngine = TranslationEngine(context)
    val pinManager: PinManager = PinManager(context, settings)
    val vault: Vault = Vault(context, fileStore, pinManager)
}
