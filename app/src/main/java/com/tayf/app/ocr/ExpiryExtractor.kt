package com.tayf.app.ocr

import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import kotlin.math.max

internal data class DateHit(val epoch: Long, val index: Int)

/** Finds Gregorian and Hijri dates in free text (Arabic or Latin digits, numeric and textual months). */
internal object DateParser {

    private fun cleanWord(w: String): String {
        val d = Normalizer.normalize(w.lowercase(), Normalizer.Form.NFD)
        return d.filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه')
    }

    private val months: Map<String, Int> = buildMap {
        val table = mapOf(
            1 to listOf("january", "jan", "janvier", "janv", "يناير", "جانفي", "كانون"),
            2 to listOf("february", "feb", "fevrier", "fevr", "فبراير", "فيفري", "شباط"),
            3 to listOf("march", "mar", "mars", "مارس", "اذار"),
            4 to listOf("april", "apr", "avril", "avr", "ابريل", "افريل", "نيسان"),
            5 to listOf("may", "mai", "مايو", "ماي", "ايار"),
            6 to listOf("june", "jun", "juin", "يونيو", "جوان", "حزيران"),
            7 to listOf("july", "jul", "juillet", "juil", "يوليو", "جويلية", "تموز"),
            8 to listOf("august", "aug", "aout", "اغسطس", "اوت", "اب"),
            9 to listOf("september", "sep", "sept", "septembre", "سبتمبر", "ايلول"),
            10 to listOf("october", "oct", "octobre", "اكتوبر", "تشرين"),
            11 to listOf("november", "nov", "novembre", "نوفمبر"),
            12 to listOf("december", "dec", "decembre", "ديسمبر")
        )
        for ((m, names) in table) for (n in names) put(cleanWord(n), m)
    }

    fun normalizeDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    private fun toEpoch(date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun build(yearIn: Int, month: Int, day: Int, index: Int): DateHit? {
        var year = yearIn
        if (year in 0..99) year += 2000
        if (month !in 1..12 || day !in 1..31) return null
        return try {
            val date = when (year) {
                in 1300..1600 -> LocalDate.from(HijrahDate.of(year, month, day))
                in 1900..2100 -> LocalDate.of(year, month, day)
                else -> return null
            }
            DateHit(toEpoch(date), index)
        } catch (e: DateTimeException) {
            null
        }
    }

    private val iso = Regex("""(?<!\d)(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)""")
    private val dmy = Regex("""(?<!\d)(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?!\d)""")
    private val dayMonthYear = Regex("""(?<!\d)(\d{1,2})\s*(?:st|nd|rd|th|er)?\s+(\p{L}{3,12})\.?,?\s+(\d{4})(?!\d)""")
    private val monthDayYear = Regex("""(\p{L}{3,12})\.?\s+(\d{1,2}),?\s+(\d{4})(?!\d)""")

    fun findAll(raw: String): List<DateHit> {
        val text = normalizeDigits(raw)
        val hits = ArrayList<DateHit>()
        for (m in iso.findAll(text)) {
            build(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.range.first)?.let { hits += it }
        }
        for (m in dmy.findAll(text)) {
            var d = m.groupValues[1].toInt()
            var mo = m.groupValues[2].toInt()
            if (mo > 12 && d <= 12) { val t = d; d = mo; mo = t }
            build(m.groupValues[3].toInt(), mo, d, m.range.first)?.let { hits += it }
        }
        for (m in dayMonthYear.findAll(text)) {
            val month = months[cleanWord(m.groupValues[2])] ?: continue
            build(m.groupValues[3].toInt(), month, m.groupValues[1].toInt(), m.range.first)?.let { hits += it }
        }
        for (m in monthDayYear.findAll(text)) {
            val month = months[cleanWord(m.groupValues[1])] ?: continue
            build(m.groupValues[3].toInt(), month, m.groupValues[2].toInt(), m.range.first)?.let { hits += it }
        }
        return hits.sortedBy { it.index }
    }
}

object ExpiryExtractor {
    private val keywords = listOf(
        "expir", "valid until", "valid thru", "good thru", "valable", "validité", "validite", "jusqu",
        "échéance", "echeance", "تاريخ الانتهاء", "الانتهاء", "ينتهي", "تنتهي", "صالحة حتى", "صالح حتى",
        "صلاحية", "الصلاحية", "exp.", "exp:", "exp ", "until"
    )

    /** Most likely expiry date (epoch millis) or null. Keyword-marked dates win; otherwise the latest future date. */
    fun extract(text: String): Long? {
        if (text.isBlank()) return null
        val hits = DateParser.findAll(text)
        if (hits.isEmpty()) return null
        val lower = DateParser.normalizeDigits(text).lowercase()
        val keyed = hits.filter { h ->
            val start = max(0, h.index - 45)
            val end = h.index.coerceAtMost(lower.length)
            val window = if (start < end) lower.substring(start, end) else ""
            keywords.any { window.contains(it) }
        }
        if (keyed.isNotEmpty()) return keyed.maxOf { it.epoch }
        val now = System.currentTimeMillis()
        return hits.filter { it.epoch > now }.maxOfOrNull { it.epoch }
    }
}
