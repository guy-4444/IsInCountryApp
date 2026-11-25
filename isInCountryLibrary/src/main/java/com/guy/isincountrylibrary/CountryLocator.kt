package com.guy.isincountrylibrary

import android.content.Context
import java.io.InputStream
import java.util.Locale

data class CountryInfo(
    val name: String,
    val isoA2: String,
    val isoA3: String
)

/**
 * Checks whether [lat], [lon] lies inside the country matching [isoCode] (supports ISO alpha-2,
 * ISO alpha-3, or numeric/admin codes, case-insensitive).
 */
fun isInCountry(lat: Double, lon: Double, isoCode: String): Boolean =
    CountryLocator.isInCountry(lat, lon, isoCode)

/**
 * Returns the ISO country code (e.g. "IL", "US", "FR") containing [lat], [lon],
 * or `null` if the coordinate is in the sea/ocean (or invalid).
 */
fun inWhichCountry(lat: Double, lon: Double): String? =
    CountryLocator.inWhichCountry(lat, lon)

/**
 * Returns `true` if [lat], [lon] is a valid Earth coordinate that is not inside any country polygon.
 */
fun isInSea(lat: Double, lon: Double): Boolean =
    CountryLocator.isInSea(lat, lon)

object CountryLocator {

    const val DEFAULT_ASSET_FILE = "countries.json"
    private const val EPSILON = 1e-9

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var assetName: String = DEFAULT_ASSET_FILE

    @Volatile
    private var cachedIndex: CountryIndex? = null

    private val lock = Any()

    /**
     * Registers [context] so `assets/countries.json` can be loaded automatically on demand.
     */
    @JvmStatic
    @JvmOverloads
    fun registerContext(context: Context, assetFileName: String = DEFAULT_ASSET_FILE) {
        appContext = context.applicationContext
        assetName = assetFileName
    }

    /**
     * Initializes and preloads the country polygons from [context]'s assets.
     */
    @JvmStatic
    @JvmOverloads
    fun init(context: Context, assetFileName: String = DEFAULT_ASSET_FILE) {
        registerContext(context, assetFileName)
        ensureLoaded()
    }

    /**
     * Loads country polygons directly from an [InputStream] (useful for custom sources or unit tests).
     */
    @JvmStatic
    fun loadFromStream(inputStream: InputStream) {
        val json = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        loadFromJson(json)
    }

    /**
     * Loads country polygons directly from a GeoJSON [json] string.
     */
    @JvmStatic
    fun loadFromJson(json: String) {
        val features = GeoJsonParser.parseCountries(json)
        synchronized(lock) {
            cachedIndex = CountryIndex.fromFeatures(features)
        }
    }

    /**
     * Returns `true` if ([lat], [lon]) falls inside the country identified by [isoCode].
     * Accepts 2-letter (e.g. "IL", "US", "FR") or 3-letter (e.g. "ISR", "USA", "FRA") ISO codes.
     */
    @JvmStatic
    fun isInCountry(lat: Double, lon: Double, isoCode: String): Boolean {
        if (!isValidCoordinate(lat, lon)) return false
        val normalizedIso = isoCode.trim().uppercase(Locale.ROOT)
        if (normalizedIso.isEmpty()) return false

        val matches = ensureLoaded().byCode[normalizedIso] ?: return false
        return matches.any { it.contains(lat, lon) }
    }

    /**
     * Returns the ISO code (alpha-2, e.g. "IL", "US") of the country containing ([lat], [lon]),
     * or `null` if the point is in the sea/ocean or outside valid Earth bounds.
     */
    @JvmStatic
    fun inWhichCountry(lat: Double, lon: Double): String? =
        getCountryInfo(lat, lon)?.isoA2

    /**
     * Returns `true` if ([lat], [lon]) is a valid coordinate on Earth and does not fall inside
     * any country's land polygon.
     */
    @JvmStatic
    fun isInSea(lat: Double, lon: Double): Boolean {
        if (!isValidCoordinate(lat, lon)) return false
        return getCountryInfo(lat, lon) == null
    }

    /**
     * Returns detailed [CountryInfo] (`name`, `isoA2`, `isoA3`) for ([lat], [lon]), or `null` if in sea.
     */
    @JvmStatic
    fun getCountryInfo(lat: Double, lon: Double): CountryInfo? {
        if (!isValidCoordinate(lat, lon)) return null
        val features = ensureLoaded().features
        for (feature in features) {
            if (feature.contains(lat, lon)) {
                return feature.info
            }
        }
        return null
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    private fun ensureLoaded(): CountryIndex {
        cachedIndex?.let { return it }
        synchronized(lock) {
            cachedIndex?.let { return it }
            val ctx = appContext
                ?: throw IllegalStateException(
                    "CountryLocator is not initialized. Call CountryLocator.init(context) or CountryLocator.loadFromJson(json) first."
                )
            val json = ctx.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val index = CountryIndex.fromFeatures(GeoJsonParser.parseCountries(json))
            cachedIndex = index
            return index
        }
    }

    private class CountryIndex(
        val features: List<CountryFeature>,
        val byCode: Map<String, List<CountryFeature>>
    ) {
        companion object {
            fun fromFeatures(features: List<CountryFeature>): CountryIndex {
                val map = HashMap<String, MutableList<CountryFeature>>(features.size * 3)
                for (feature in features) {
                    for (alias in feature.aliases) {
                        map.getOrPut(alias) { ArrayList(1) }.add(feature)
                    }
                }
                return CountryIndex(features = features, byCode = map)
            }
        }
    }

    internal const val GEOM_EPSILON = EPSILON
}

internal class CountryFeature(
    val info: CountryInfo,
    val aliases: Set<String>,
    val polygons: List<CountryPolygon>,
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double
) {
    fun contains(lat: Double, lon: Double): Boolean {
        val eps = CountryLocator.GEOM_EPSILON
        if (lon < minLon - eps || lon > maxLon + eps || lat < minLat - eps || lat > maxLat + eps) {
            return false
        }
        return polygons.any { it.contains(lon, lat) }
    }
}

internal class CountryPolygon(
    val exterior: LinearRing,
    val holes: List<LinearRing>
) {
    fun contains(lon: Double, lat: Double): Boolean {
        if (!exterior.contains(lon, lat, includeBoundary = true)) return false
        for (hole in holes) {
            if (hole.contains(lon, lat, includeBoundary = false)) return false
        }
        return true
    }
}

internal class LinearRing(
    val coords: DoubleArray,
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double
) {
    fun contains(lon: Double, lat: Double, includeBoundary: Boolean): Boolean {
        val eps = CountryLocator.GEOM_EPSILON
        if (lon < minLon - eps || lon > maxLon + eps || lat < minLat - eps || lat > maxLat + eps) {
            return false
        }

        val vertexCount = coords.size ushr 1
        if (vertexCount < 3) return false

        val epsSq = eps * eps
        var inside = false
        var j = vertexCount - 1

        for (i in 0 until vertexCount) {
            val x1 = coords[j shl 1]
            val y1 = coords[(j shl 1) + 1]
            val x2 = coords[i shl 1]
            val y2 = coords[(i shl 1) + 1]

            // Check whether point lies directly on segment (x1, y1) -> (x2, y2)
            val segMinX = minOf(x1, x2) - eps
            val segMaxX = maxOf(x1, x2) + eps
            val segMinY = minOf(y1, y2) - eps
            val segMaxY = maxOf(y1, y2) + eps
            if (lon in segMinX..segMaxX && lat in segMinY..segMaxY) {
                val dx = x2 - x1
                val dy = y2 - y1
                val lenSq = dx * dx + dy * dy
                if (lenSq <= epsSq) {
                    val px = lon - x1
                    val py = lat - y1
                    if (px * px + py * py <= epsSq) {
                        return includeBoundary
                    }
                } else {
                    val cross = (lat - y1) * dx - (lon - x1) * dy
                    if (cross * cross <= epsSq * lenSq) {
                        return includeBoundary
                    }
                }
            }

            // Ray-casting along +longitude
            if ((y1 > lat) != (y2 > lat)) {
                val xIntersect = (x2 - x1) * (lat - y1) / (y2 - y1) + x1
                if (lon < xIntersect) {
                    inside = !inside
                }
            }

            j = i
        }

        return inside
    }
}
