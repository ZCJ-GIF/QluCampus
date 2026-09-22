// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.campus

import com.dawncourse.core.domain.model.*
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** 学校的表格型 OOXML 读写器：不执行公式，不依赖桌面 Office/AWT。 */
object XlsxTableCodec {
    const val MAX_DOWNLOAD = 8 * 1024 * 1024
    private const val MAX_UNPACKED = 32 * 1024 * 1024
    private const val MAX_ROWS = 20000
    private const val MAX_COLUMNS = 80

    fun read(bytes: ByteArray): List<List<String>> {
        if (bytes.size > MAX_DOWNLOAD) throw SchoolDataException("学校返回的文件过大")
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4b.toByte()) {
            val text = bytes.take(4096).toByteArray().toString(Charsets.UTF_8)
            if (text.contains("登录") || text.contains("login", true)) throw SchoolLoginRequired()
            throw SchoolDataException("学校未返回 XLSX 表格，原有成绩已保留")
        }
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > 512) throw SchoolDataException("表格结构超出限制")
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_UNPACKED) throw SchoolDataException("表格解压后过大")
                    output.write(buffer, 0, n)
                }
                if (entries.put(entry.name, output.toByteArray()) != null) throw SchoolDataException("表格包含重复文件")
            }
        }
        fun xml(path: String) = parseXml(entries[path] ?: throw SchoolDataException("表格缺少必要内容：$path"))
        val shared = entries["xl/sharedStrings.xml"]?.let { data ->
            parseXml(data).getElementsByTagNameNS("*", "si").let { list ->
                (0 until list.length).map { i ->
                    val ts = (list.item(i) as Element).getElementsByTagNameNS("*", "t")
                    (0 until ts.length).joinToString("") { ts.item(it).textContent }
                }
            }
        }.orEmpty()
        val sheet = xml("xl/workbook.xml").getElementsByTagNameNS("*", "sheet").item(0) as? Element
            ?: throw SchoolDataException("工作簿没有工作表")
        val id = sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
        val relationships = xml("xl/_rels/workbook.xml.rels").getElementsByTagNameNS("*", "Relationship")
        val relation = (0 until relationships.length).map { relationships.item(it) as Element }
            .firstOrNull { it.getAttribute("Id") == id && it.getAttribute("TargetMode") != "External" }
            ?: throw SchoolDataException("工作表关系无效")
        val target = relation.getAttribute("Target")
        val path = if (target.startsWith("/")) target.drop(1) else "xl/$target"
        if (!path.startsWith("xl/") || path.contains("..") || path.contains('\\')) throw SchoolDataException("工作表路径无效")
        val rows = xml(path).getElementsByTagNameNS("*", "row")
        if (rows.length > MAX_ROWS) throw SchoolDataException("成绩行数超出限制")
        return (0 until rows.length).map { rowIndex ->
            val cells = (rows.item(rowIndex) as Element).getElementsByTagNameNS("*", "c")
            val result = mutableListOf<String>()
            for (i in 0 until cells.length) {
                val c = cells.item(i) as Element
                val letters = c.getAttribute("r").takeWhile { it.isLetter() }
                val index = if (letters.isEmpty()) i else letters.fold(0) { n, ch -> n * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1
                if (index !in 0 until MAX_COLUMNS) throw SchoolDataException("表格列数超出限制")
                while (result.size <= index) result += ""
                val v = c.getElementsByTagNameNS("*", "v").item(0)?.textContent.orEmpty()
                result[index] = when (c.getAttribute("t")) {
                    "s" -> shared.getOrNull(v.toIntOrNull() ?: -1) ?: throw SchoolDataException("表格字符串索引无效")
                    "inlineStr" -> c.getElementsByTagNameNS("*", "t").let { ts ->
                        (0 until ts.length).joinToString("") { ts.item(it).textContent }
                    }
                    else -> v
                }.trim()
            }
            result
        }.filter { row -> row.any { it.isNotBlank() } }
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        val text = bytes.toString(Charsets.UTF_8)
        if (text.contains("<!DOCTYPE", true) || text.contains("<!ENTITY", true)) throw SchoolDataException("表格 XML 包含不支持的声明")
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }.newDocumentBuilder().apply { setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) } }
            .parse(ByteArrayInputStream(bytes))
    }

    fun write(sheets: List<Pair<String, List<List<String>>>>): ByteArray {
        require(sheets.isNotEmpty())
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
            val relNs = "http://schemas.openxmlformats.org/package/2006/relationships"
            val docRel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" + sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } + "</Types>")
            entry("_rels/.rels", """<Relationships xmlns="$relNs"><Relationship Id="rId1" Type="$docRel/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", """<workbook xmlns="$ns" xmlns:r="$docRel"><sheets>""" + sheets.mapIndexed { i, s -> "<sheet name=\"${escape(s.first)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets></workbook>")
            entry("xl/_rels/workbook.xml.rels", """<Relationships xmlns="$relNs">""" + sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"$docRel/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } + "</Relationships>")
            sheets.forEachIndexed { i, s ->
                val rows = s.second.mapIndexed { r, values -> "<row r=\"${r + 1}\">" + values.mapIndexed { c, value ->
                    var n = c + 1; var column = ""
                    while (n > 0) { column = ('A' + (n - 1) % 26) + column; n = (n - 1) / 26 }
                    "<c r=\"$column${r + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>"
                }.joinToString("") + "</row>" }.joinToString("")
                entry("xl/worksheets/sheet${i + 1}.xml", """<worksheet xmlns="$ns"><sheetData>$rows</sheetData></worksheet>""")
            }
        }
        return output.toByteArray()
    }

    private fun escape(value: String) = value.filter { it >= ' ' || it == '\n' || it == '\t' || it == '\r' }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
