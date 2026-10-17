package com.tayf.app.core

import java.time.Instant
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    /** Localized date. Arabic-Indic digits are used when the app language is Arabic. */
    fun formatDate(epochMillis: Long, hijri: Boolean): String {
        val locale = Locale.getDefault()
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val text = try {
            if (hijri) {
                val h = HijrahDate.from(date)
                DateTimeFormatter.ofPattern("d MMMM yyyy", locale).withChronology(HijrahChronology.INSTANCE).format(h)
            } else {
                DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(date)
            }
        } catch (e: Exception) {
            date.toString()
        }
        return if (locale.language == "ar") toArabicDigits(text) else text
    }

    fun toArabicDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '0'..'9') ('\u0660' + (c - '0')) else c)
        return sb.toString()
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        val cut = if (cleaned.length > 100) cleaned.substring(0, 100).trim() else cleaned
        return cut.ifEmpty { "scan" }
    }
}
