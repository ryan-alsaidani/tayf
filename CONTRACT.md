# TAYF (طيف) — PROJECT CONTRACT v1.1

You are a senior Android engineer. You write ONE batch of a larger project.
Other batches are written separately (by you, in other chats). The project only builds if every batch obeys this contract EXACTLY.

---------------------------------------------------------------
## 0. HARD RULES FOR THE AI
---------------------------------------------------------------
1. Output every file in its own code block, preceded by a line: `FILE: <path relative to project root>`.
2. Write COMPLETE files. Never write "...", "rest of code", "TODO", placeholders, or pseudo-code.
3. Write ONLY the files listed for the current batch. Do not touch other files.
4. Use the names, packages, signatures and enums in section 4 EXACTLY (same spelling, same parameter order, same types). Never rename. Never invent a different signature for an existing one.
5. You MAY add private helpers inside a file. You may NOT add new public classes/functions that other batches need unless the batch brief asks for it.
6. If you need something from another batch, use it ONLY through its contract signature in section 4.
7. Kotlin only (no Java). Jetpack Compose + Material3 for UI. No XML layouts.
8. Use only the libraries in section 2, with those artifact names. Do not add dependencies. Do not use deprecated APIs when a current replacement exists.
9. The app works fully offline. The ONLY network use allowed is one-time downloads of language packs (OCR traineddata, translation models) triggered explicitly by the user, implemented in `ocr/LanguagePacks.kt` and via ML Kit's own model downloader. No analytics, no ads, no tracking, no other network code anywhere.
10. Permissions: `android.permission.CAMERA` and `android.permission.INTERNET` (INTERNET only for rule 9). Everything else through the Storage Access Framework / MediaStore / app-private storage.
11. Every user-visible string goes in `res/values/strings.xml` (Arabic, default), `res/values-en/strings.xml` (English) and `res/values-fr/strings.xml` (French), referenced by `R.string.*`. No hardcoded UI text in Kotlin.
12. Heavy work (image processing, OCR, PDF, encryption, DB) runs on `Dispatchers.Default` / `Dispatchers.IO`. Never block the main thread. Free Bitmaps you no longer need.
13. The code must compile on the first try. Before answering, re-check imports, nullability, and that every referenced symbol exists either in your files or in section 4.
14. End your answer with a list: `FILES WRITTEN:` (paths) and `ASSUMPTIONS:` (anything you assumed).

---------------------------------------------------------------
## 1. PRODUCT
---------------------------------------------------------------
Android-only, professional document scanner that works fully offline after the user downloads the language packs he wants. Arabic RTL first, dark theme only. UI languages: Arabic (default), English, French. Document languages (OCR + translation): all languages the engines support.
FINAL APK SIZE IS IRRELEVANT. In every technical choice pick the best-quality, most powerful, most professional option, even if it is heavy. The goal is a flawless, first-release product, not a prototype. Visual identity: gold on dark brown, premium, custom icons (draw with Compose `ImageVector`/Canvas, no icon library beyond Material icons-core).

Core: edge detection + manual corner adjustment, auto-capture, batch scan, scan modes (single, batch, ID card two sides on one page, book, whiteboard), filters (original, black & white, gray, enhanced color), shadow removal, rotate/crop, brightness/contrast, reorder/delete pages, add pages to an existing doc, split a doc, annotations (signature, text, highlight, draw), library (folders, search, sort, rename, favorites), export (PDF, JPG, PNG, page size A4/A5/..., compression, password-protected PDF, print), Arabic RTL UI, PIN lock, backup/restore to a file, offline OCR (Arabic + English) with searchable PDF and TXT export.

Extras (the differentiators):
- E1 Compress to target size ("under 200 KB"): auto-tune quality/scale to hit the size.
- E2 Protected ID copy: blur chosen regions + diagonal watermark text ("For X only, dated Y"), burned into the pixels (flattened, irreversible).
- E3 Capture quality guard: live warning for blur/shake, glare, shadow; suggests retake.
- E4 Document type recognition: ID / invoice / book / handwritten; auto-applies best filter + page size.
- E5 Smart naming after OCR, e.g. "فاتورة ستاغ - 2026-10-13"; supports Hijri and Gregorian dates.
- E6 Full-library text search; highlights the match location on the page image.
- E7 Long-document scan: move the phone along a long receipt; frames are stitched into one page.
- E8 Open-book split: two facing pages in one photo -> two separate, corrected pages.
- E9 Encrypted vault: password/PIN-protected folder, files encrypted on device.
- E10 In-app "expiring soon" list: extract expiry dates from documents (in-app only, no notifications).
- E11 Offline translation: translate the OCR text of a page/document between all supported languages, on device, shown side-by-side with the original text. Models are downloaded once, then it works with no internet. Export the translation as TXT/DOCX.

---------------------------------------------------------------
## 2. TECH STACK (fixed)
---------------------------------------------------------------
- Language: Kotlin (latest stable 2.x), JDK 17, Gradle Kotlin DSL with version catalog (`gradle/libs.versions.toml`), AGP latest stable.
- minSdk 26, targetSdk/compileSdk = latest stable API level.
- Package / applicationId: `com.tayf.app`. App name: `طيف` (ar) / `Tayf` (en).
- UI: Jetpack Compose (BOM latest stable), Material3, Navigation Compose, `activity-compose`, lifecycle-viewmodel-compose.
- Camera: CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`).
- Vision: OpenCV Android from Maven Central (`org.opencv:opencv`, 4.9.0 or newer): edges, perspective warp, filters, shadow removal, blur/glare detection, stitching.
- OCR (all languages):
  * ML Kit bundled recognizers (no Play Services download): `text-recognition` (Latin), `text-recognition-chinese`, `text-recognition-devanagari`, `text-recognition-japanese`, `text-recognition-korean`.
  * Tesseract4Android (`cz.adaptech.tesseract4android:tesseract4android`) with `tessdata_best` models for everything else, especially Arabic, Persian, Urdu, Hebrew, Cyrillic, Greek, Thai, etc. `ara` and `eng` ship in `assets/tessdata/`; all other languages are downloaded on demand by `LanguagePacks` into `filesDir/tessdata/` from `https://github.com/tesseract-ocr/tessdata_best/raw/main/<lang>.traineddata` (plain `HttpsURLConnection`, with progress; a pack counts as installed only if the file is non-empty and Tesseract can load it).
  * `OcrEngine` picks the engine per script, runs both when the script is mixed (e.g. Arabic + Latin), and merges results by position without duplicating text.
- Translation (offline): ML Kit On-Device Translation (`com.google.mlkit:translate`) with automatic language detection (`com.google.mlkit:language-id`). Models are downloaded once through `RemoteModelManager`, then work offline. The UI lets the user manage downloaded models and delete them.
- PDF: PdfBox-Android (`com.tom-roush:pdfbox-android`) for password protection and invisible text layer (embed `assets/fonts/NotoNaskhArabic-Regular.ttf`). Page rendering for import: `android.graphics.pdf.PdfRenderer`.
- DOCX / XLSX export: write minimal OOXML by hand with `java.util.zip.ZipOutputStream`. NO Apache POI.
- Database: Room (+ KSP) with FTS4 for OCR text. Settings: Preferences DataStore.
- Images: Coil (compose). JSON: kotlinx-serialization-json.
- Security: `androidx.biometric`, `androidx.security:security-crypto` (EncryptedFile / MasterKey), PIN stored as salted PBKDF2 hash.
- Printing: `androidx.print` (PrintHelper) / PrintManager.
- Dates: `java.time` and `java.time.chrono.HijrahDate` (API 26+).
- DI: NONE (no Hilt, no Koin). Manual container `AppContainer` (section 4).
- Release signing: read keystore from env vars `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (provided by GitHub Secrets). If absent, fall back to debug signing.
- CI: `.github/workflows/build.yml`. Known GitHub warnings from our previous build that MUST NOT appear again: "Node.js 20 is deprecated", "setup-java v4 is deprecated", "ubuntu-latest label will migrate". Therefore: use the LATEST major versions of `actions/checkout`, `actions/setup-java` (Temurin 17 or newer as AGP requires), `gradle/actions/setup-gradle`, `actions/upload-artifact` (versions that run on Node 24), and pin the runner to an explicit label (`ubuntu-24.04`, not `ubuntu-latest`). The previous build used Gradle 8.10.2 and failed: set an explicit Gradle version that is COMPATIBLE with the chosen AGP and Kotlin versions (state the compatibility matrix you relied on in ASSUMPTIONS). Run `gradle assembleRelease --stacktrace` (the wrapper JAR is not in the repo) and upload the APK. Also upload the Gradle build reports as an artifact when the build fails, so errors can be read. Also enable R8 minify + resource shrinking with correct keep rules for OpenCV, PdfBox, Tesseract, ML Kit, kotlinx-serialization, Room.
- ABI: `arm64-v8a` and `armeabi-v7a` (ndk abiFilters), universal APK output.

---------------------------------------------------------------
## 3. PROJECT TREE (root folder: `tayf/`)
---------------------------------------------------------------
```
tayf/
├─ CONTRACT.md
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
├─ gradle/libs.versions.toml
├─ .gitignore
├─ .github/workflows/build.yml
└─ app/
   ├─ build.gradle.kts
   ├─ proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ assets/ (tessdata/ara.traineddata, tessdata/eng.traineddata, fonts/NotoNaskhArabic-Regular.ttf)  <- added by the user, NOT by the AI
      ├─ res/
      │  ├─ values/{strings.xml, colors.xml, themes.xml}
      │  ├─ values-en/strings.xml
      │  ├─ values-fr/strings.xml
      │  ├─ xml/{file_paths.xml, backup_rules.xml, data_extraction_rules.xml}
      │  ├─ mipmap-anydpi-v26/{ic_launcher.xml, ic_launcher_round.xml}
      │  └─ drawable/ic_launcher_background.xml   (foreground image is added by the user: drawable/ic_launcher_foreground.png)
      └─ java/com/tayf/app/
         ├─ TayfApp.kt
         ├─ MainActivity.kt
         ├─ AppContainer.kt
         ├─ core/
         │  ├─ Models.kt
         │  ├─ Result.kt
         │  ├─ Dispatchers.kt
         │  ├─ FileStore.kt
         │  └─ Formatters.kt
         ├─ data/
         │  ├─ db/{TayfDatabase.kt, Entities.kt, Daos.kt}
         │  ├─ DocumentRepository.kt
         │  ├─ SettingsRepository.kt
         │  └─ BackupManager.kt
         ├─ vision/
         │  ├─ OpenCvInit.kt
         │  ├─ EdgeDetector.kt
         │  ├─ PerspectiveWarp.kt
         │  ├─ ImageEnhancer.kt
         │  ├─ QualityAnalyzer.kt
         │  ├─ DocTypeClassifier.kt
         │  ├─ LongScanStitcher.kt
         │  └─ BookSplitter.kt
         ├─ ocr/
         │  ├─ OcrEngine.kt
         │  ├─ SmartNamer.kt
         │  ├─ ExpiryExtractor.kt
         │  ├─ SearchIndex.kt
         │  ├─ LanguagePacks.kt
         │  └─ TranslationEngine.kt
         ├─ export/
         │  ├─ PdfExporter.kt
         │  ├─ ImageExporter.kt
         │  ├─ TextExporters.kt        (TXT, DOCX, XLSX)
         │  ├─ SizeCompressor.kt
         │  ├─ PrintHelper.kt
         │  └─ ShareHelper.kt
         ├─ privacy/
         │  ├─ Redactor.kt
         │  ├─ Watermarker.kt
         │  ├─ Vault.kt
         │  └─ PinManager.kt
         └─ ui/
            ├─ theme/{Color.kt, Theme.kt, Type.kt, Icons.kt}
            ├─ nav/{Routes.kt, NavGraph.kt}
            ├─ components/{Common.kt, PageThumb.kt, CornerEditor.kt, ColorPickerRow.kt}
            ├─ camera/{CameraScreen.kt, CameraViewModel.kt}
            ├─ crop/{CropScreen.kt, CropViewModel.kt}
            ├─ editor/{PageEditorScreen.kt, PageEditorViewModel.kt}
            ├─ document/{DocumentScreen.kt, DocumentViewModel.kt}
            ├─ library/{LibraryScreen.kt, LibraryViewModel.kt, SearchScreen.kt, SearchViewModel.kt, ExpiringScreen.kt}
            ├─ annotate/{AnnotateScreen.kt, AnnotateViewModel.kt, SignaturePad.kt}
            ├─ privacy/{ProtectedCopyScreen.kt, LockScreen.kt, VaultScreen.kt}
            ├─ export/{ExportSheet.kt, ExportViewModel.kt}
            ├─ translate/{TranslateScreen.kt, TranslateViewModel.kt, LanguagePacksScreen.kt}
            └─ settings/{SettingsScreen.kt, SettingsViewModel.kt}
```

---------------------------------------------------------------
## 4. SHARED CONTRACT (exact signatures)
---------------------------------------------------------------
All code is in package `com.tayf.app.<folder>` matching its folder. Imports between packages use these exact names.

### 4.1 core/Models.kt  (package com.tayf.app.core)
```kotlin
enum class FilterType { ORIGINAL, BLACK_WHITE, GRAY, ENHANCED_COLOR }
enum class DocType { UNKNOWN, ID_CARD, INVOICE, BOOK, HANDWRITTEN, WHITEBOARD }
enum class ScanMode { SINGLE, BATCH, ID_CARD, BOOK, WHITEBOARD, LONG }
enum class PageSize(val widthMm: Int, val heightMm: Int) {
    AUTO(0, 0), A3(297, 420), A4(210, 297), A5(148, 210), A6(105, 148), LETTER(216, 279), LEGAL(216, 356)
}
enum class ExportFormat { PDF, JPG, PNG, TXT, DOCX, XLSX }
enum class SortOrder { DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC }

/** Normalized corner coordinates in 0f..1f relative to the ORIGINAL image. Order: topLeft, topRight, bottomRight, bottomLeft. */
data class Quad(
    val tlX: Float, val tlY: Float, val trX: Float, val trY: Float,
    val brX: Float, val brY: Float, val blX: Float, val blY: Float
) {
    companion object { fun full(): Quad = Quad(0f,0f, 1f,0f, 1f,1f, 0f,1f) }
}

data class PageModel(
    val id: Long,
    val documentId: Long,
    val position: Int,
    val originalPath: String,      // absolute path, camera/import image
    val processedPath: String,     // absolute path, after crop+filter (what the user sees)
    val thumbPath: String,
    val quad: Quad,
    val filter: FilterType,
    val brightness: Float,         // -1f..1f, default 0f
    val contrast: Float,           // 0.5f..2f, default 1f
    val rotation: Int,             // 0, 90, 180, 270
    val ocrText: String?,
    val ocrBlocksJson: String?     // JSON list of OcrBlock (see 4.6)
)

data class DocumentModel(
    val id: Long,
    val name: String,
    val folderId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean,
    val isVault: Boolean,
    val docType: DocType,
    val pageCount: Int,
    val expiryDate: Long?          // epoch millis or null
)

data class FolderModel(val id: Long, val name: String, val isVault: Boolean, val createdAt: Long)

data class SearchHit(
    val documentId: Long, val documentName: String, val pageId: Long, val pageIndex: Int,
    val snippet: String,
    val boxes: List<NormRect>      // highlight boxes on the processed page image
)
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) // 0..1
```

### 4.2 core/Result.kt, Dispatchers.kt, FileStore.kt, Formatters.kt
```kotlin
// Result.kt
sealed class Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>()
    data class Fail(val message: String, val cause: Throwable? = null) : Outcome<Nothing>()
}
// Dispatchers.kt
object AppDispatchers { val io = kotlinx.coroutines.Dispatchers.IO; val cpu = kotlinx.coroutines.Dispatchers.Default; val main = kotlinx.coroutines.Dispatchers.Main }
// FileStore.kt
class FileStore(private val context: android.content.Context) {
    fun newOriginalFile(): java.io.File          // filesDir/scans/originals/<uuid>.jpg
    fun newProcessedFile(): java.io.File         // filesDir/scans/processed/<uuid>.jpg
    fun newThumbFile(): java.io.File             // filesDir/scans/thumbs/<uuid>.jpg
    fun newTempFile(ext: String): java.io.File   // cacheDir/tmp/<uuid>.<ext>
    fun exportDir(): java.io.File                // cacheDir/exports  (shared through FileProvider)
    fun vaultDir(): java.io.File                 // filesDir/vault
    fun uriFor(file: java.io.File): android.net.Uri   // FileProvider authority "com.tayf.app.fileprovider"
    fun deleteQuietly(path: String?)
    fun clearTemp()
}
// Formatters.kt
object Formatters {
    fun formatBytes(bytes: Long): String
    fun formatDate(epochMillis: Long, hijri: Boolean): String   // localized, Arabic digits when locale is ar
    fun sanitizeFileName(name: String): String
}
```

### 4.3 AppContainer.kt (package com.tayf.app)
```kotlin
class AppContainer(val context: android.content.Context) {
    val fileStore: FileStore
    val database: com.tayf.app.data.db.TayfDatabase
    val documents: com.tayf.app.data.DocumentRepository
    val settings: com.tayf.app.data.SettingsRepository
    val backup: com.tayf.app.data.BackupManager
    val ocr: com.tayf.app.ocr.OcrEngine
    val searchIndex: com.tayf.app.ocr.SearchIndex
    val languagePacks: com.tayf.app.ocr.LanguagePacks
    val translator: com.tayf.app.ocr.TranslationEngine
    val pinManager: com.tayf.app.privacy.PinManager
    val vault: com.tayf.app.privacy.Vault
}
// TayfApp.kt: class TayfApp : Application() { lateinit var container: AppContainer; onCreate -> container = AppContainer(this); OpenCvInit.ensure() }
// Access from anywhere: (context.applicationContext as TayfApp).container
```

### 4.4 data/ (package com.tayf.app.data)
```kotlin
class DocumentRepository(db: TayfDatabase, fileStore: FileStore) {
    fun observeDocuments(folderId: Long?, sort: SortOrder, favoritesOnly: Boolean): kotlinx.coroutines.flow.Flow<List<DocumentModel>>
    fun observeFolders(includeVault: Boolean): kotlinx.coroutines.flow.Flow<List<FolderModel>>
    fun observeDocument(id: Long): kotlinx.coroutines.flow.Flow<DocumentModel?>
    fun observePages(documentId: Long): kotlinx.coroutines.flow.Flow<List<PageModel>>
    suspend fun getDocument(id: Long): DocumentModel?
    suspend fun getPages(documentId: Long): List<PageModel>
    suspend fun getPage(pageId: Long): PageModel?
    suspend fun createDocument(name: String, folderId: Long?, docType: DocType, isVault: Boolean = false): Long
    suspend fun addPage(documentId: Long, page: PageModel): Long          // id ignored (autogenerated), position = last
    suspend fun updatePage(page: PageModel)
    suspend fun deletePages(pageIds: List<Long>)                          // also deletes files
    suspend fun reorderPages(documentId: Long, orderedPageIds: List<Long>)
    suspend fun renameDocument(id: Long, newName: String)
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun moveDocument(id: Long, folderId: Long?)
    suspend fun setDocType(id: Long, type: DocType)
    suspend fun setExpiry(id: Long, expiryDate: Long?)
    suspend fun deleteDocument(id: Long)                                  // also deletes files
    suspend fun splitDocument(id: Long, pageIdsForNewDoc: List<Long>, newName: String): Long   // returns new doc id
    suspend fun mergeInto(targetId: Long, sourceId: Long)                 // appends source pages, deletes source doc
    suspend fun createFolder(name: String, isVault: Boolean = false): Long
    suspend fun renameFolder(id: Long, name: String)
    suspend fun deleteFolder(id: Long)                                    // documents move to root
    suspend fun listExpiring(withinDays: Int): List<DocumentModel>
    suspend fun saveOcr(pageId: Long, text: String, blocksJson: String)   // also updates FTS index
}

class SettingsRepository(context: android.content.Context) {
    val hijriDates: Flow<Boolean>;       suspend fun setHijriDates(v: Boolean)
    val defaultPageSize: Flow<PageSize>; suspend fun setDefaultPageSize(v: PageSize)
    val defaultFilter: Flow<FilterType>; suspend fun setDefaultFilter(v: FilterType)
    val autoCapture: Flow<Boolean>;      suspend fun setAutoCapture(v: Boolean)
    val qualityGuard: Flow<Boolean>;     suspend fun setQualityGuard(v: Boolean)
    val pinEnabled: Flow<Boolean>;       suspend fun setPinEnabled(v: Boolean)
    val biometricEnabled: Flow<Boolean>; suspend fun setBiometricEnabled(v: Boolean)
    val lockTimeoutSeconds: Flow<Int>;   suspend fun setLockTimeoutSeconds(v: Int)
    val jpegQuality: Flow<Int>;          suspend fun setJpegQuality(v: Int)   // 50..100, default 90
}

class BackupManager(context: Context, db: TayfDatabase, fileStore: FileStore) {
    suspend fun createBackup(target: android.net.Uri, password: String?): Outcome<Long>      // returns bytes written; zip of DB + images, AES if password
    suspend fun restoreBackup(source: android.net.Uri, password: String?): Outcome<Unit>
}
```
Room entities (`Entities.kt`) mirror the models: `DocumentEntity`, `PageEntity`, `FolderEntity`, FTS entity `PageFts(ocrText)`. DAOs in `Daos.kt`. `TayfDatabase` is `RoomDatabase` version 1, `exportSchema = false`, with `TayfDatabase.build(context): TayfDatabase`.

### 4.5 vision/ (package com.tayf.app.vision)
```kotlin
object OpenCvInit { fun ensure() }                       // OpenCVLoader.initLocal(), idempotent

object EdgeDetector {
    /** Returns the document quad (normalized) or null if no confident quad found. Fast: must run < 60 ms on a 640px-wide downscale. */
    fun detect(bitmap: android.graphics.Bitmap): Quad?
    /** Same, plus a 0f..1f confidence, used by auto-capture stability check. */
    fun detectWithConfidence(bitmap: android.graphics.Bitmap): Pair<Quad, Float>?
}

object PerspectiveWarp {
    /** Crops and straightens the region described by quad from `source`. Output keeps native resolution (no upscaling beyond 4000px long side). */
    fun warp(source: android.graphics.Bitmap, quad: Quad): android.graphics.Bitmap
    fun rotate(bitmap: android.graphics.Bitmap, degrees: Int): android.graphics.Bitmap
}

object ImageEnhancer {
    fun apply(bitmap: android.graphics.Bitmap, filter: FilterType, brightness: Float, contrast: Float, removeShadows: Boolean): android.graphics.Bitmap
    fun removeShadows(bitmap: android.graphics.Bitmap): android.graphics.Bitmap
    fun makeThumbnail(bitmap: android.graphics.Bitmap, maxSidePx: Int = 320): android.graphics.Bitmap
}

data class QualityReport(
    val blurScore: Float,        // 0..1, 1 = very blurry
    val glareScore: Float,       // 0..1
    val shadowScore: Float,      // 0..1
    val isBlurry: Boolean, val hasGlare: Boolean, val hasShadow: Boolean
) { val isGood: Boolean get() = !isBlurry && !hasGlare && !hasShadow }
object QualityAnalyzer { fun analyze(bitmap: android.graphics.Bitmap): QualityReport }   // fast, < 40 ms on a 480px downscale

object DocTypeClassifier {
    /** Heuristics only (aspect ratio, text density, ocr keywords when provided). */
    fun classify(bitmap: android.graphics.Bitmap, ocrText: String?): DocType
    fun suggestedFilter(type: DocType): FilterType
    fun suggestedPageSize(type: DocType): PageSize
}

object LongScanStitcher {
    /** Stitches frames captured while moving along a long document into one tall bitmap. Frames are in capture order. Returns null on failure. */
    fun stitch(frames: List<android.graphics.Bitmap>): android.graphics.Bitmap?
}

object BookSplitter {
    /** Splits a photo of an open book into [left, right] corrected pages. Returns null if the gutter cannot be found. */
    fun split(bitmap: android.graphics.Bitmap): List<android.graphics.Bitmap>?
}
```

### 4.6 ocr/ (package com.tayf.app.ocr)
```kotlin
@kotlinx.serialization.Serializable
data class OcrBlock(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float, val lang: String) // normalized 0..1
data class OcrResult(val fullText: String, val blocks: List<OcrBlock>, val languageGuess: String)  // "ar", "en", "mixed"

class OcrEngine(context: android.content.Context) {
    suspend fun recognize(bitmap: android.graphics.Bitmap): Outcome<OcrResult>
    fun close()
}
object OcrJson { fun encode(blocks: List<OcrBlock>): String; fun decode(json: String?): List<OcrBlock> }

object SmartNamer {
    /** e.g. "فاتورة ستاغ - 2026-10-13". Uses first meaningful lines + detected date + doc type. Fallback: "مستند - <date>". */
    fun suggestName(text: String, type: DocType, nowMillis: Long, hijri: Boolean): String
}

/** A language the app can OCR and/or translate. `code` = BCP-47 (e.g. "ar", "en", "fr", "zh"). */
data class LangInfo(val code: String, val displayName: String, val tessCode: String?, val canTranslate: Boolean, val ocrEngine: String /* "mlkit" | "tesseract" | "both" */)

sealed class PackState {
    object NotInstalled : PackState()
    data class Downloading(val progress: Float) : PackState()   // 0f..1f
    object Installed : PackState()
    data class Failed(val message: String) : PackState()
}

class LanguagePacks(context: android.content.Context, fileStore: com.tayf.app.core.FileStore) {
    fun supportedLanguages(): List<LangInfo>                          // full list, sorted by displayName
    fun ocrState(code: String): kotlinx.coroutines.flow.Flow<PackState>
    suspend fun downloadOcrPack(code: String): Outcome<Unit>
    suspend fun deleteOcrPack(code: String)
    fun installedOcrCodes(): Set<String>                              // includes bundled ara + eng
    /** Active OCR languages chosen by the user (multi-select, default ["ar","en"]). */
    val activeOcrLanguages: kotlinx.coroutines.flow.Flow<Set<String>>
    suspend fun setActiveOcrLanguages(codes: Set<String>)
}

class TranslationEngine(context: android.content.Context) {
    suspend fun detectLanguage(text: String): String?                 // BCP-47 or null if undetermined
    fun modelState(code: String): kotlinx.coroutines.flow.Flow<PackState>
    suspend fun downloadModel(code: String): Outcome<Unit>
    suspend fun deleteModel(code: String)
    suspend fun installedModels(): Set<String>
    suspend fun translate(text: String, from: String, to: String): Outcome<String>   // offline once both models are installed; pivots through English when needed
    suspend fun translatePage(page: com.tayf.app.core.PageModel, from: String?, to: String): Outcome<String>
    fun close()
}

object ExpiryExtractor {
    /** Finds the most likely expiry date (Gregorian or Hijri, Arabic or Latin digits, many formats). Returns epoch millis or null. */
    fun extract(text: String): Long?
}

class SearchIndex(db: TayfDatabase) {
    suspend fun search(query: String, includeVault: Boolean): List<SearchHit>   // FTS + box lookup from ocrBlocksJson
}
```

### 4.7 export/ (package com.tayf.app.export)
```kotlin
data class PdfOptions(
    val pageSize: PageSize = PageSize.A4,
    val password: String? = null,
    val searchable: Boolean = true,      // invisible OCR text layer when ocr data exists
    val jpegQuality: Int = 90,
    val maxSidePx: Int? = null
)

object PdfExporter {
    suspend fun export(pages: List<PageModel>, target: java.io.File, options: PdfOptions): Outcome<java.io.File>
}
object ImageExporter {
    suspend fun export(pages: List<PageModel>, format: ExportFormat /* JPG or PNG */, quality: Int, outDir: java.io.File, baseName: String): Outcome<List<java.io.File>>
}
object TextExporters {
    suspend fun exportTxt(pages: List<PageModel>, target: java.io.File): Outcome<java.io.File>
    suspend fun exportDocx(pages: List<PageModel>, target: java.io.File): Outcome<java.io.File>   // RTL-aware paragraphs
    suspend fun exportXlsx(pages: List<PageModel>, target: java.io.File): Outcome<java.io.File>   // one sheet per page; lines -> rows, tab/2+ spaces -> columns
}
object SizeCompressor {
    /** Produces a PDF whose size is <= targetBytes, searching quality and scale (binary search). Returns Fail if impossible even at the minimum acceptable quality. */
    suspend fun compressPdfToSize(pages: List<PageModel>, targetBytes: Long, options: PdfOptions, target: java.io.File): Outcome<java.io.File>
    suspend fun compressImageToSize(bitmap: android.graphics.Bitmap, targetBytes: Long, target: java.io.File): Outcome<java.io.File>
}
object PrintHelper { fun printPages(activity: android.app.Activity, pages: List<PageModel>, jobName: String) }
object ShareHelper {
    fun share(context: android.content.Context, files: List<java.io.File>, mime: String)
    fun saveToUri(context: android.content.Context, file: java.io.File, target: android.net.Uri): Outcome<Unit>
}
```

### 4.8 privacy/ (package com.tayf.app.privacy)
```kotlin
data class RedactionRegion(val rect: NormRect, val style: RedactionStyle)
enum class RedactionStyle { BLACK_BOX, HEAVY_BLUR }

object Redactor { fun apply(bitmap: android.graphics.Bitmap, regions: List<RedactionRegion>): android.graphics.Bitmap }   // irreversible, new bitmap
object Watermarker {
    /** Diagonal repeating text over the whole image, burned into pixels. */
    fun apply(bitmap: android.graphics.Bitmap, text: String, opacity: Float = 0.35f, colorArgb: Int = 0xFF8B0000.toInt()): android.graphics.Bitmap
}

class PinManager(context: android.content.Context, settings: SettingsRepository) {
    suspend fun isPinSet(): Boolean
    suspend fun setPin(pin: String)
    suspend fun verify(pin: String): Boolean
    suspend fun clearPin(pin: String): Boolean
    fun isLockedNow(): Boolean
    fun markUnlocked()
    fun lock()
}

class Vault(context: Context, fileStore: FileStore, pinManager: PinManager) {
    suspend fun isInitialized(): Boolean
    suspend fun create(password: String): Outcome<Unit>
    suspend fun unlock(password: String): Boolean
    fun lock()
    fun isUnlocked(): Boolean
    suspend fun encryptPageFiles(page: PageModel): Outcome<PageModel>   // moves files into vaultDir as EncryptedFile, returns updated model
    suspend fun decryptToTemp(path: String): Outcome<java.io.File>
}
```

### 4.9 ui/ (package com.tayf.app.ui.*)
Navigation routes (`ui/nav/Routes.kt`):
```kotlin
object Routes {
    const val LIBRARY = "library"
    const val CAMERA = "camera/{mode}?docId={docId}"        // mode = ScanMode.name, docId optional (add pages to existing doc)
    const val CROP = "crop/{pageId}"
    const val EDITOR = "editor/{pageId}"
    const val DOCUMENT = "document/{docId}"
    const val SEARCH = "search"
    const val EXPIRING = "expiring"
    const val ANNOTATE = "annotate/{pageId}"
    const val PROTECTED_COPY = "protected/{pageId}"
    const val VAULT = "vault"
    const val LOCK = "lock"
    const val SETTINGS = "settings"
    fun camera(mode: ScanMode, docId: Long? = null): String
    fun crop(pageId: Long): String
    fun editor(pageId: Long): String
    fun document(docId: Long): String
    fun annotate(pageId: Long): String
    fun protectedCopy(pageId: Long): String
}
@Composable fun TayfNavGraph(container: AppContainer, navController: NavHostController)
```
Theme (`ui/theme`): `@Composable fun TayfTheme(content: @Composable () -> Unit)` — dark only, RTL via `LayoutDirection` from locale. Colors (exact): `TayfBrown = Color(0xFF1E140D)`, `TayfBrownLight = Color(0xFF2B1D13)`, `TayfSurface = Color(0xFF34251A)`, `TayfGold = Color(0xFFC9A24B)`, `TayfGoldLight = Color(0xFFE6C77A)`, `TayfText = Color(0xFFF3E9D8)`, `TayfTextDim = Color(0xFFB8A68C)`, `TayfDanger = Color(0xFFE5645A)`, `TayfOk = Color(0xFF7FB77E)`.
`Icons.kt`: `object TayfIcons { val Scan, Library, Folder, Star, Search, Lock, Vault, Share, Print, Export, Crop, Rotate, Filter, Sign, Text, Highlight, Draw, Blur, Watermark, Compress, Settings, Camera, Gallery, Delete, Add, Back, More, Expiry : ImageVector }` — hand-drawn gold line icons.
Shared composables (`ui/components`):
```kotlin
@Composable fun CornerEditor(bitmap: android.graphics.Bitmap, quad: Quad, onQuadChange: (Quad) -> Unit, modifier: Modifier = Modifier)  // draggable 4 handles + magnifier loupe
@Composable fun PageThumb(page: PageModel, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier)
@Composable fun TayfTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {})
@Composable fun ColorPickerRow(selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier)
```
Each screen: `@Composable fun <Name>Screen(container: AppContainer, navController: NavHostController, ...route args)` and a `ViewModel` created with a `viewModelFactory { initializer { ... } }` receiving `AppContainer`.

---------------------------------------------------------------
## 5. BATCH PLAN (each chat = one batch)
---------------------------------------------------------------
- B0 Build system: all gradle files, `.gitignore`, workflow, manifest, resources (strings ar/en with every string used later: use clear key names `screen_element`, I will ask later batches to append), themes, FileProvider paths, launcher icon XML, proguard.
- B1 Foundation: `core/*`, `data/db/*`, `DocumentRepository`, `SettingsRepository`, `AppContainer`, `TayfApp`, `MainActivity`.
- B2 Theme + navigation + components: `ui/theme/*`, `ui/nav/*`, `ui/components/*`.
- B3 Vision core: `OpenCvInit`, `EdgeDetector`, `PerspectiveWarp`, `ImageEnhancer`, `QualityAnalyzer`.
- B4 Camera + crop + editor screens (scan modes, auto-capture, quality guard UI, batch, ID card, whiteboard, import from gallery).
- B5 Library + document screens (folders, sort, rename, favorites, reorder/delete/add pages, split, merge).
- B6 OCR: `OcrEngine`, `LanguagePacks`, `SmartNamer`, `ExpiryExtractor`, `SearchIndex`, `DocTypeClassifier`, search + expiring screens.
- B6b Translation: `TranslationEngine`, translate screen, language packs screen (OCR packs + translation models), export of translations.
- B7 Export: `PdfExporter`, `ImageExporter`, `TextExporters`, `SizeCompressor`, `PrintHelper`, `ShareHelper`, export sheet.
- B8 Annotation: annotate screens, signature pad.
- B9 Privacy: `Redactor`, `Watermarker`, `PinManager`, `Vault`, protected copy, lock, vault screens.
- B10 Extras: `LongScanStitcher`, `BookSplitter` and their camera integration.
- B11 Settings + backup: `BackupManager`, settings screen, final wiring check of `NavGraph`, missing strings.
- B12 Audit: I paste compile errors; you fix only the files named.

---------------------------------------------------------------
## 6. QUALITY BAR
---------------------------------------------------------------
- Multilingual: all scripts (LTR and RTL, mixed lines) must render and export correctly; never corrupt text. Normalize Unicode (NFC), keep bidi order, handle Arabic/Persian/Hindi digits in search and date parsing.
- Arabic text: never reverse or reshape manually; rely on the platform (Compose / Paint with `Layout.DIR`), and use the embedded Noto Naskh font for PDF text layers.
- Large images: decode with `inSampleSize`/`ImageDecoder` and a 4000px long-side cap; always handle EXIF rotation.
- No crashes on null/empty data: every screen has empty, loading and error states.
- R8-safe: no reflection without keep rules.
- Code style: small functions, KDoc on public API, no unused code.

---------------------------------------------------------------
## 7. HOW A REQUEST WILL LOOK
---------------------------------------------------------------
I paste this whole contract, then:
`BATCH: B<n>` + `FILES: <list>` + extra notes.
You answer with the files only, in the format of rule 0.1, then FILES WRITTEN / ASSUMPTIONS.
