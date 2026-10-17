-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod, SourceFile, LineNumberTable

# OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# PdfBox-Android
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn org.spongycastle.**

# Tesseract4Android
-keep class com.googlecode.tesseract.android.** { *; }
-dontwarn com.googlecode.tesseract.android.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# kotlinx-serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.tayf.app.**$$serializer { *; }
-keepclassmembers class com.tayf.app.** { *** Companion; }
-keepclasseswithmembers class com.tayf.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Security crypto / Tink
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn javax.lang.model.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.checkerframework.**

# App models used through reflection-free paths but kept for safety
-keep class com.tayf.app.core.** { *; }
-keep class com.tayf.app.ocr.OcrBlock { *; }
