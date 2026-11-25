package com.guy.isincountrylibrary

import java.util.Locale
import kotlin.collections.get

internal object GeoJsonParser {

    fun parseCountries(json: String): List<CountryFeature> {
        val scanner = JsonScanner(json)
        val features = ArrayList<CountryFeature>(180)

        scanner.expect('{')
        if (scanner.peek() != '}') {
            while (true) {
                val key = scanner.readString()
                scanner.expect(':')
                if (key == "features" && scanner.peek() == '[') {
                    scanner.expect('[')
                    if (scanner.peek() != ']') {
                        while (true) {
                            parseFeature(scanner)?.let { features.add(it) }
                            if (scanner.peek() == ',') {
                                scanner.expect(',')
                            } else {
                                break
                            }
                        }
                    }
                    scanner.expect(']')
                } else {
                    scanner.skipValue()
                }
                if (scanner.peek() == ',') {
                    scanner.expect(',')
                } else {
                    break
                }
            }
        }
        scanner.expect('}')
        return features
    }

    private fun parseFeature(scanner: JsonScanner): CountryFeature? {
        if (scanner.peek() != '{') {
            scanner.skipValue()
            return null
        }

        var featureId: String? = null
        var name: String? = null
        var adminName: String? = null
        var rawIsoA2: String? = null
        var rawIsoA3: String? = null
        var rawIsoA3Eh: String? = null
        var rawIsoN3: String? = null
        var rawAdm0A3: String? = null
        var rawWbA2: String? = null
        var rawWbA3: String? = null
        var polygons: List<CountryPolygon> = emptyList()

        scanner.expect('{')
        if (scanner.peek() != '}') {
            while (true) {
                val key = scanner.readString()
                scanner.expect(':')
                when (key) {
                    "id" -> featureId = scanner.readScalarAsStringOrNull()
                    "properties" -> {
                        if (scanner.peek() == '{') {
                            scanner.expect('{')
                            if (scanner.peek() != '}') {
                                while (true) {
                                    val propKey = scanner.readString()
                                    scanner.expect(':')
                                    when (propKey) {
                                        "NAME" -> name = scanner.readScalarAsStringOrNull()
                                        "ADMIN" -> adminName = scanner.readScalarAsStringOrNull()
                                        "ISO_A2" -> rawIsoA2 = scanner.readScalarAsStringOrNull()
                                        "ISO_A3" -> rawIsoA3 = scanner.readScalarAsStringOrNull()
                                        "ISO_A3_EH" -> rawIsoA3Eh = scanner.readScalarAsStringOrNull()
                                        "ISO_N3" -> rawIsoN3 = scanner.readScalarAsStringOrNull()
                                        "ADM0_A3" -> rawAdm0A3 = scanner.readScalarAsStringOrNull()
                                        "WB_A2" -> rawWbA2 = scanner.readScalarAsStringOrNull()
                                        "WB_A3" -> rawWbA3 = scanner.readScalarAsStringOrNull()
                                        else -> scanner.skipValue()
                                    }
                                    if (scanner.peek() == ',') {
                                        scanner.expect(',')
                                    } else {
                                        break
                                    }
                                }
                            }
                            scanner.expect('}')
                        } else {
                            scanner.skipValue()
                        }
                    }
                    "geometry" -> polygons = parseGeometry(scanner)
                    else -> scanner.skipValue()
                }
                if (scanner.peek() == ',') {
                    scanner.expect(',')
                } else {
                    break
                }
            }
        }
        scanner.expect('}')

        if (polygons.isEmpty()) return null

        val adm0A3 = cleanCode(rawAdm0A3)
        val isoA3 = cleanCode(rawIsoA3)
            ?: cleanCode(rawIsoA3Eh)
            ?: cleanCode(rawWbA3)?.takeIf { it != "KSV" }
            ?: when (adm0A3) {
                "KOS" -> "XKX"
                else -> adm0A3
            }
            ?: cleanCode(featureId)?.takeIf { it.length == 3 }
            ?: ""

        val isoA2 = cleanCode(rawIsoA2)
            ?: cleanCode(rawWbA2)?.takeIf { it != "KV" }
            ?: FALLBACK_ISO3_TO_ISO2[isoA3]
            ?: FALLBACK_ISO3_TO_ISO2[adm0A3]
            ?: cleanCode(featureId)?.takeIf { it.length == 2 }
            ?: isoA3

        if (isoA2.isEmpty() && isoA3.isEmpty()) return null

        val aliases = LinkedHashSet<String>(6)
        fun addAlias(code: String?) {
            cleanCode(code)?.let { aliases.add(it) }
        }
        addAlias(isoA2)
        addAlias(isoA3)
        addAlias(adm0A3)
        addAlias(rawIsoA3Eh)
        addAlias(rawWbA2)
        addAlias(rawWbA3)
        addAlias(rawIsoN3)
        addAlias(featureId)
        if (isoA2 == "GB") aliases.add("UK")
        if (isoA3 == "NOR") aliases.add("578")

        val resolvedName = name?.takeIf { it.isNotBlank() }
            ?: adminName?.takeIf { it.isNotBlank() }
            ?: isoA2

        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        for (poly in polygons) {
            val ext = poly.exterior
            if (ext.minLon < minLon) minLon = ext.minLon
            if (ext.maxLon > maxLon) maxLon = ext.maxLon
            if (ext.minLat < minLat) minLat = ext.minLat
            if (ext.maxLat > maxLat) maxLat = ext.maxLat
        }

        return CountryFeature(
            info = CountryInfo(
                name = resolvedName,
                isoA2 = isoA2,
                isoA3 = isoA3
            ),
            aliases = aliases,
            polygons = polygons,
            minLon = minLon,
            maxLon = maxLon,
            minLat = minLat,
            maxLat = maxLat
        )
    }

    private fun parseGeometry(scanner: JsonScanner): List<CountryPolygon> {
        if (scanner.peek() != '{') {
            scanner.skipValue()
            return emptyList()
        }

        var polygons: List<CountryPolygon> = emptyList()
        scanner.expect('{')
        if (scanner.peek() != '}') {
            while (true) {
                val key = scanner.readString()
                scanner.expect(':')
                if (key == "coordinates") {
                    polygons = when (scanner.countLeadingBrackets()) {
                        3 -> listOfNotNull(parsePolygon(scanner))
                        4 -> parseMultiPolygon(scanner)
                        else -> {
                            scanner.skipValue()
                            emptyList()
                        }
                    }
                } else {
                    scanner.skipValue()
                }
                if (scanner.peek() == ',') {
                    scanner.expect(',')
                } else {
                    break
                }
            }
        }
        scanner.expect('}')
        return polygons
    }

    private fun parseMultiPolygon(scanner: JsonScanner): List<CountryPolygon> {
        val result = ArrayList<CountryPolygon>(4)
        scanner.expect('[')
        if (scanner.peek() != ']') {
            while (true) {
                parsePolygon(scanner)?.let { result.add(it) }
                if (scanner.peek() == ',') {
                    scanner.expect(',')
                } else {
                    break
                }
            }
        }
        scanner.expect(']')
        return result
    }

    private fun parsePolygon(scanner: JsonScanner): CountryPolygon? {
        val rings = ArrayList<LinearRing>(2)
        scanner.expect('[')
        if (scanner.peek() != ']') {
            while (true) {
                parseLinearRing(scanner)?.let { rings.add(it) }
                if (scanner.peek() == ',') {
                    scanner.expect(',')
                } else {
                    break
                }
            }
        }
        scanner.expect(']')
        if (rings.isEmpty()) return null
        val holes = if (rings.size > 1) rings.subList(1, rings.size).toList() else emptyList()
        return CountryPolygon(exterior = rings[0], holes = holes)
    }

    private fun parseLinearRing(scanner: JsonScanner): LinearRing? {
        var coords = DoubleArray(64)
        var size = 0
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY

        scanner.expect('[')
        if (scanner.peek() != ']') {
            while (true) {
                scanner.expect('[')
                val lon = scanner.readDouble()
                scanner.expect(',')
                val lat = scanner.readDouble()
                while (scanner.peek() == ',') {
                    scanner.expect(',')
                    scanner.skipValue()
                }
                scanner.expect(']')

                if (size + 2 > coords.size) {
                    coords = coords.copyOf(coords.size * 2)
                }
                coords[size++] = lon
                coords[size++] = lat

                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat

                if (scanner.peek() == ',') {
                    scanner.expect(',')
                } else {
                    break
                }
            }
        }
        scanner.expect(']')

        if (size < 6) return null
        return LinearRing(
            coords = coords.copyOf(size),
            minLon = minLon,
            maxLon = maxLon,
            minLat = minLat,
            maxLat = maxLat
        )
    }

    private fun cleanCode(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim().uppercase(Locale.ROOT)
        if (
            trimmed.isEmpty() ||
            trimmed.startsWith("-99") ||
            trimmed == "-099" ||
            trimmed == "NULL"
        ) {
            return null
        }
        return trimmed
    }

    private val FALLBACK_ISO3_TO_ISO2 = mapOf(
        "FRA" to "FR",
        "NOR" to "NO",
        "KOS" to "XK",
        "XKX" to "XK"
    )

    private class JsonScanner(private val src: String) {
        private val len = src.length
        private var pos = if (len > 0 && src[0] == '\uFEFF') 1 else 0

        fun skipWhitespace() {
            while (pos < len) {
                when (src[pos]) {
                    ' ', '\t', '\n', '\r' -> pos++
                    else -> return
                }
            }
        }

        fun peek(): Char {
            skipWhitespace()
            return if (pos < len) src[pos] else '\u0000'
        }

        fun expect(expected: Char) {
            skipWhitespace()
            if (pos >= len || src[pos] != expected) {
                throw IllegalArgumentException(
                    "Expected '$expected' at index $pos in GeoJSON"
                )
            }
            pos++
        }

        fun countLeadingBrackets(): Int {
            skipWhitespace()
            var idx = pos
            var count = 0
            while (idx < len) {
                when (src[idx]) {
                    '[' -> {
                        count++
                        idx++
                    }
                    ' ', '\t', '\n', '\r' -> idx++
                    else -> break
                }
            }
            return count
        }

        fun readString(): String {
            expect('"')
            val start = pos
            while (pos < len) {
                val c = src[pos]
                if (c == '"') {
                    val result = src.substring(start, pos)
                    pos++
                    return result
                }
                if (c == '\\') {
                    return readEscapedString(start)
                }
                pos++
            }
            throw IllegalArgumentException("Unterminated string in GeoJSON")
        }

        private fun readEscapedString(start: Int): String {
            val sb = StringBuilder(pos - start + 16)
            sb.append(src, start, pos)
            while (pos < len) {
                val c = src[pos++]
                if (c == '"') {
                    return sb.toString()
                }
                if (c == '\\') {
                    if (pos >= len) throw IllegalArgumentException("Unexpected end in escape")
                    when (val esc = src[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > len) {
                                throw IllegalArgumentException("Invalid unicode escape")
                            }
                            val hex = src.substring(pos, pos + 4)
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated escaped string in GeoJSON")
        }

        fun readDouble(): Double {
            skipWhitespace()
            val start = pos
            while (pos < len) {
                when (src[pos]) {
                    in '0'..'9', '-', '+', '.', 'e', 'E' -> pos++
                    else -> break
                }
            }
            if (pos == start) {
                throw IllegalArgumentException("Expected number at index $pos in GeoJSON")
            }
            return src.substring(start, pos).toDouble()
        }

        fun readScalarAsStringOrNull(): String? {
            return when (peek()) {
                '"' -> readString()
                'n' -> {
                    skipLiteral("null")
                    null
                }
                't' -> {
                    skipLiteral("true")
                    "true"
                }
                'f' -> {
                    skipLiteral("false")
                    "false"
                }
                in '0'..'9', '-', '+' -> {
                    val start = pos
                    readDouble()
                    src.substring(start, pos)
                }
                else -> {
                    skipValue()
                    null
                }
            }
        }

        fun skipValue() {
            when (peek()) {
                '{' -> {
                    expect('{')
                    if (peek() != '}') {
                        while (true) {
                            readString()
                            expect(':')
                            skipValue()
                            if (peek() == ',') expect(',') else break
                        }
                    }
                    expect('}')
                }
                '[' -> {
                    expect('[')
                    if (peek() != ']') {
                        while (true) {
                            skipValue()
                            if (peek() == ',') expect(',') else break
                        }
                    }
                    expect(']')
                }
                '"' -> readString()
                't' -> skipLiteral("true")
                'f' -> skipLiteral("false")
                'n' -> skipLiteral("null")
                else -> readDouble()
            }
        }

        private fun skipLiteral(literal: String) {
            skipWhitespace()
            if (!src.startsWith(literal, pos)) {
                throw IllegalArgumentException("Expected '$literal' at index $pos")
            }
            pos += literal.length
        }
    }
}
