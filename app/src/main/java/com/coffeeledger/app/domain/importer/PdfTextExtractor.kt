package com.coffeeledger.app.domain.importer

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * A small, dependency-free PDF text extractor for text-based bank statements.
 *
 * Statements are financial documents, so pulling in a third-party PDF library - and with it
 * whatever that library does at runtime - would undercut the point of the app. Instead this
 * reads only what it needs: content streams are inflated, and text-showing operators are
 * replayed to recover lines.
 *
 * Scanned (image-only) statements contain no text operators and produce no output; the
 * import screen says so rather than pretending.
 */
object PdfTextExtractor {

    private const val LATIN1 = "ISO-8859-1"

    /** One item on the content-stream operand stack. */
    private data class Operand(val isText: Boolean, val value: String)

    fun extract(bytes: ByteArray): String {
        val raw = String(bytes, charset(LATIN1))
        if (!raw.startsWith("%PDF")) throw ImportException("That file is not a PDF.")
        if (ENCRYPT_MARKER.containsMatchIn(raw)) {
            throw ImportException("This PDF is password protected. Remove the password and try again.")
        }

        val pages = StringBuilder()
        for (stream in contentStreams(bytes, raw)) {
            val text = renderContentStream(stream)
            if (text.isNotBlank()) {
                pages.append(text)
                if (!text.endsWith("\n")) pages.append('\n')
            }
        }
        return pages.toString()
    }

    private val ENCRYPT_MARKER = Regex("""/Encrypt\s+\d+\s+\d+\s+R""")

    /** Returns the decoded text of every stream that looks like page content. */
    private fun contentStreams(bytes: ByteArray, raw: String): List<String> {
        val result = mutableListOf<String>()
        var searchFrom = 0
        while (true) {
            val streamAt = raw.indexOf("stream", searchFrom)
            if (streamAt < 0) break
            // Skip the "endstream" keyword itself, which also contains "stream".
            if (streamAt >= 3 && raw.regionMatches(streamAt - 3, "end", 0, 3)) {
                searchFrom = streamAt + 6
                continue
            }
            val dictionary = raw.substring(maxOf(0, streamAt - 900), streamAt)
            var dataStart = streamAt + "stream".length
            if (dataStart < raw.length && raw[dataStart] == '\r') dataStart++
            if (dataStart < raw.length && raw[dataStart] == '\n') dataStart++

            val endAt = raw.indexOf("endstream", dataStart)
            if (endAt < 0) break
            var dataEnd = endAt
            while (dataEnd > dataStart && (raw[dataEnd - 1] == '\n' || raw[dataEnd - 1] == '\r')) dataEnd--

            searchFrom = endAt + "endstream".length
            if (dataEnd <= dataStart) continue
            // Object streams hold object definitions, not drawing operators.
            if (dictionary.contains("/ObjStm") || dictionary.contains("/XRef") ||
                dictionary.contains("/Image")
            ) {
                continue
            }

            val slice = bytes.copyOfRange(dataStart, dataEnd)
            val decoded = when {
                dictionary.contains("/FlateDecode") -> inflate(slice) ?: continue
                dictionary.contains("/Filter") -> continue // LZW, DCT and friends are out of scope
                else -> slice
            }
            val decodedText = String(decoded, charset(LATIN1))
            if (decodedText.contains("Tj") || decodedText.contains("TJ")) {
                result.add(decodedText)
            }
        }
        return result
    }

    private fun inflate(data: ByteArray): ByteArray? {
        for (nowrap in listOf(false, true)) {
            val inflater = Inflater(nowrap)
            try {
                inflater.setInput(data)
                val output = ByteArrayOutputStream(data.size * 4)
                val buffer = ByteArray(16 * 1024)
                while (!inflater.finished()) {
                    val read = inflater.inflate(buffer)
                    if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                    output.write(buffer, 0, read)
                }
                if (output.size() > 0) return output.toByteArray()
            } catch (_: Exception) {
                // Try the other window setting before giving up on this stream.
            } finally {
                inflater.end()
            }
        }
        return null
    }

    /**
     * Replays the text-showing operators of one content stream. Vertical moves become line
     * breaks, and wide negative kerning inside a TJ array becomes a space, which is what
     * separates the columns of a statement table.
     */
    internal fun renderContentStream(content: String): String {
        val out = StringBuilder()
        val line = StringBuilder()
        var lastY: Double? = null
        val operands = ArrayDeque<Operand>()

        fun flushLine() {
            val text = line.toString().trim()
            if (text.isNotEmpty()) out.append(text).append('\n')
            line.clear()
        }

        var index = 0
        while (index < content.length) {
            val char = content[index]
            when {
                char == '(' -> {
                    val (text, next) = readLiteralString(content, index)
                    operands.addLast(Operand(true, text))
                    index = next
                }
                char == '<' && index + 1 < content.length && content[index + 1] == '<' -> {
                    index = skipDictionary(content, index)
                }
                char == '<' -> {
                    val (text, next) = readHexString(content, index)
                    operands.addLast(Operand(true, text))
                    index = next
                }
                char == '[' -> {
                    val (text, next) = readArray(content, index)
                    operands.addLast(Operand(true, text))
                    index = next
                }
                char == '%' -> {
                    while (index < content.length && content[index] != '\n') index++
                }
                char.isWhitespace() -> index++
                else -> {
                    val start = index
                    while (index < content.length && !content[index].isWhitespace() &&
                        content[index] !in "()<>[]{}/%"
                    ) {
                        index++
                    }
                    if (index == start) {
                        index++
                        continue
                    }
                    val token = content.substring(start, index)
                    when (token) {
                        "Tj", "TJ" -> {
                            popText(operands)?.let { line.append(it) }
                            operands.clear()
                        }
                        "'", "\"" -> {
                            flushLine()
                            popText(operands)?.let { line.append(it) }
                            operands.clear()
                        }
                        "Td", "TD" -> {
                            val dy = numberOperand(operands) ?: 0.0
                            if (dy != 0.0) flushLine()
                            operands.clear()
                        }
                        "Tm" -> {
                            val y = numberOperand(operands)
                            val previous = lastY
                            if (y != null && previous != null && kotlin.math.abs(y - previous) > 0.5) {
                                flushLine()
                            }
                            if (y != null) lastY = y
                            operands.clear()
                        }
                        "T*", "ET", "BT" -> {
                            flushLine()
                            operands.clear()
                        }
                        else -> {
                            if (token.toDoubleOrNull() != null) operands.addLast(Operand(false, token))
                            else operands.clear()
                        }
                    }
                }
            }
        }
        flushLine()
        return out.toString()
    }

    private fun popText(operands: ArrayDeque<Operand>): String? =
        operands.lastOrNull { it.isText }?.value

    private fun numberOperand(operands: ArrayDeque<Operand>): Double? =
        operands.lastOrNull()?.takeUnless { it.isText }?.value?.toDoubleOrNull()

    private fun readLiteralString(content: String, start: Int): Pair<String, Int> {
        val builder = StringBuilder()
        var depth = 0
        var index = start
        while (index < content.length) {
            val char = content[index]
            when {
                char == '\\' && index + 1 < content.length -> {
                    when (val next = content[index + 1]) {
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'b', 'f' -> builder.append(' ')
                        '\n' -> Unit
                        in '0'..'7' -> {
                            var digits = ""
                            var offset = index + 1
                            while (offset < content.length && digits.length < 3 && content[offset] in '0'..'7') {
                                digits += content[offset]
                                offset++
                            }
                            digits.toIntOrNull(8)?.let { builder.append(it.toChar()) }
                            index = offset
                            continue
                        }
                        else -> builder.append(next)
                    }
                    index += 2
                    continue
                }
                char == '(' -> {
                    depth++
                    if (depth > 1) builder.append(char)
                }
                char == ')' -> {
                    depth--
                    if (depth == 0) return builder.toString() to (index + 1)
                    builder.append(char)
                }
                else -> builder.append(char)
            }
            index++
        }
        return builder.toString() to index
    }

    private fun readHexString(content: String, start: Int): Pair<String, Int> {
        val end = content.indexOf('>', start)
        if (end < 0) return "" to content.length
        val hex = content.substring(start + 1, end).filter { !it.isWhitespace() }
        val builder = StringBuilder()
        var index = 0
        while (index + 1 < hex.length) {
            val code = hex.substring(index, index + 2).toIntOrNull(16)
            if (code != null && code in 32..255) builder.append(code.toChar())
            index += 2
        }
        return builder.toString() to (end + 1)
    }

    /** Concatenates the strings of a TJ array, turning wide kerning gaps into spaces. */
    private fun readArray(content: String, start: Int): Pair<String, Int> {
        val builder = StringBuilder()
        var index = start + 1
        while (index < content.length && content[index] != ']') {
            when (content[index]) {
                '(' -> {
                    val (text, next) = readLiteralString(content, index)
                    builder.append(text)
                    index = next
                }
                '<' -> {
                    val (text, next) = readHexString(content, index)
                    builder.append(text)
                    index = next
                }
                else -> {
                    val numberStart = index
                    while (index < content.length && content[index] !in "(<]") index++
                    val chunk = content.substring(numberStart, index).trim()
                    val kerning = chunk.split(Regex("""\s+""")).lastOrNull()?.toDoubleOrNull()
                    if (kerning != null && kerning <= -120.0 && builder.isNotEmpty() &&
                        !builder.endsWith(" ")
                    ) {
                        builder.append(' ')
                    }
                }
            }
        }
        return builder.toString() to (index + 1)
    }

    private fun skipDictionary(content: String, start: Int): Int {
        var depth = 0
        var index = start
        while (index < content.length - 1) {
            if (content[index] == '<' && content[index + 1] == '<') {
                depth++
                index += 2
                continue
            }
            if (content[index] == '>' && content[index + 1] == '>') {
                depth--
                index += 2
                if (depth == 0) return index
                continue
            }
            index++
        }
        return content.length
    }
}
