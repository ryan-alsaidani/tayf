package com.tayf.app.export

import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.Outcome
import com.tayf.app.core.PageModel
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TextExporters {

    private fun pageTexts(pages: List<PageModel>): List<String> = pages.map { it.ocrText.orEmpty().trim() }

    suspend fun exportTxt(pages: List<PageModel>, target: File): Outcome<File> = withContext(AppDispatchers.io) {
        try {
            val texts = pageTexts(pages)
            if (texts.all { it.isEmpty() }) return@withContext Outcome.Fail("No recognized text")
            target.parentFile?.mkdirs()
            target.writeText(texts.filter { it.isNotEmpty() }.joinToString("\n\n"), Charsets.UTF_8)
            Outcome.Ok(target)
        } catch (e: Throwable) {
            Outcome.Fail(e.message ?: "TXT export failed", e)
        }
    }

    /** Minimal hand-written OOXML; every paragraph is flagged RTL when its first strong character is RTL. */
    suspend fun exportDocx(pages: List<PageModel>, target: File): Outcome<File> = withContext(AppDispatchers.io) {
        try {
            val texts = pageTexts(pages)
            if (texts.all { it.isEmpty() }) return@withContext Outcome.Fail("No recognized text")
            val body = StringBuilder()
            var first = true
            for (text in texts) {
                if (text.isEmpty()) continue
                if (!first) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                first = false
                for (line in text.lines()) {
                    val rtl = isRtl(line)
                    body.append("<w:p><w:pPr>")
                    if (rtl) body.append("<w:bidi/>")
                    body.append("</w:pPr><w:r><w:rPr>")
                    if (rtl) body.append("<w:rtl/>")
                    body.append("<w:sz w:val=\"26\"/><w:szCs w:val=\"26\"/></w:rPr><w:t xml:space=\"preserve\">")
                    body.append(esc(line)).append("</w:t></w:r></w:p>")
                }
            }
            val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" +
                body +
                "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr>" +
                "</w:body></w:document>"
            val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                "</Types>"
            val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                "</Relationships>"
            writeZip(target, mapOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rels,
                "word/document.xml" to document
            ))
            Outcome.Ok(target)
        } catch (e: Throwable) {
            Outcome.Fail(e.message ?: "DOCX export failed", e)
        }
    }

    /** One sheet per page; lines become rows, tabs or 2+ spaces split columns. */
    suspend fun exportXlsx(pages: List<PageModel>, target: File): Outcome<File> = withContext(AppDispatchers.io) {
        try {
            val texts = pageTexts(pages)
            val sheets = texts.withIndex().filter { it.value.isNotEmpty() }
            if (sheets.isEmpty()) return@withContext Outcome.Fail("No recognized text")
            val files = LinkedHashMap<String, String>()
            val header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            val overrides = StringBuilder()
            val sheetRefs = StringBuilder()
            val relRefs = StringBuilder()
            sheets.forEachIndexed { n, (pageIndex, text) ->
                val id = n + 1
                val rtl = text.lines().count { isRtl(it) } * 2 > text.lines().size
                val rows = StringBuilder()
                text.lines().forEachIndexed { r, line ->
                    val cells = line.trim().split(Regex("\t|\\s{2,}"))
                    rows.append("<row r=\"${r + 1}\">")
                    cells.forEachIndexed { c, cell ->
                        if (cell.isNotEmpty()) {
                            rows.append("<c r=\"${colName(c)}${r + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                                .append(esc(cell)).append("</t></is></c>")
                        }
                    }
                    rows.append("</row>")
                }
                files["xl/worksheets/sheet$id.xml"] = header +
                    "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                    "<sheetViews><sheetView workbookViewId=\"0\"${if (rtl) " rightToLeft=\"1\"" else ""}/></sheetViews>" +
                    "<sheetData>$rows</sheetData></worksheet>"
                overrides.append("<Override PartName=\"/xl/worksheets/sheet$id.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
                sheetRefs.append("<sheet name=\"Page ${pageIndex + 1}\" sheetId=\"$id\" r:id=\"rId$id\"/>")
                relRefs.append("<Relationship Id=\"rId$id\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$id.xml\"/>")
            }
            files["[Content_Types].xml"] = header +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                overrides + "</Types>"
            files["_rels/.rels"] = header +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>"
            files["xl/workbook.xml"] = header +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets>$sheetRefs</sheets></workbook>"
            files["xl/_rels/workbook.xml.rels"] = header +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$relRefs</Relationships>"
            writeZip(target, files)
            Outcome.Ok(target)
        } catch (e: Throwable) {
            Outcome.Fail(e.message ?: "XLSX export failed", e)
        }
    }

    private fun writeZip(target: File, entries: Map<String, String>) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun isRtl(line: String): Boolean {
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            when (Character.getDirectionality(cp)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                else -> Unit
            }
            i += Character.charCount(cp)
        }
        return false
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c.code < 0x20 && c != '\t' && c != '\n' && c != '\r' -> Unit
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
