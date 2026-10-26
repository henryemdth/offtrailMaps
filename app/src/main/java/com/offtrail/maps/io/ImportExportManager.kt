package com.offtrail.maps.io

import android.content.Context
import android.net.Uri
import com.offtrail.maps.licensing.License
import com.offtrail.maps.licensing.LicenseManager
import com.offtrail.maps.model.AppRepository
import com.offtrail.maps.model.FeatureType
import com.offtrail.maps.model.GeoFeature
import com.offtrail.maps.model.LatLon
import com.offtrail.maps.model.OfftrailMap
import java.io.File
import java.util.zip.ZipFile

sealed class ImportResult {
    data class Success(val features: List<GeoFeature>) : ImportResult()
    data class GateBlocked(val required: License, val message: String) : ImportResult()
    data class Failure(val message: String) : ImportResult()
}

sealed class MapImportResult {
    data class Success(val map: OfftrailMap) : MapImportResult()
    data class GateBlocked(val message: String) : MapImportResult()
    data class Failure(val message: String) : MapImportResult()
}

/**
 * Tier-gated feature import/export pipeline. Mirrors the reference app's gates:
 *  - GPX import → Plus or Pro (error_import_layer_required_plus_for_gpx)
 *  - Shapefile import/export → Pro only (error_import_layer_required_pro_for_shp)
 *  - KML/KMZ/CSV import → free
 *  - CSV export carries placemarks only; GPX export excludes areas
 */
object ImportExportManager {

    fun importFeatures(context: Context, uri: Uri): ImportResult {
        val name = uri.lastPathSegment?.lowercase() ?: return ImportResult.Failure("Unknown file")
        return when {
            name.endsWith(".gpx") -> {
                if (!LicenseManager.canImportGpx()) {
                    ImportResult.GateBlocked(License.PLUS, "A Pro or Plus subscription is required to import GPX files.")
                } else open(context, uri) { Gpx.parse(it) }
            }
            name.endsWith(".kml") -> open(context, uri) { Kml.parse(it) }
            name.endsWith(".kmz") -> {
                val features = mutableListOf<GeoFeature>()
                val tmp = File.createTempFile("kmz", ".zip", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ZipFile(tmp).use { zip ->
                    val entry = zip.entries().asSequence().firstOrNull { it.name.lowercase().endsWith(".kml") }
                        ?: return ImportResult.Failure("No KML document inside KMZ")
                    zip.getInputStream(entry).use { features += Kml.parse(it) }
                }
                tmp.delete()
                ImportResult.Success(features)
            }
            name.endsWith(".csv") -> open(context, uri) { Csv.parse(it) }
            name.endsWith(".shp") -> {
                if (!LicenseManager.canUseShapeFiles()) {
                    ImportResult.GateBlocked(License.PRO, "Pro License Required to Import Shapefile.")
                } else open(context, uri) { Shp.parse(it) }
            }
            else -> ImportResult.Failure("Unsupported format")
        }
    }

    fun exportFeatures(context: Context, features: List<GeoFeature>, format: String): File? {
        if (format == "SHP" && !LicenseManager.canUseShapeFiles()) return null
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "export")
        dir.mkdirs()
        val file = File(dir, "offtrail_export.${format.lowercase()}")
        val body = when (format) {
            "GPX" -> Gpx.export(features)
            "KML" -> Kml.export(features)
            "CSV" -> Csv.export(features.filter { it.type == FeatureType.PLACEMARK })
            else -> return null
        }
        file.writeText(body)
        return file
    }

    private inline fun open(context: Context, uri: Uri, block: (java.io.InputStream) -> List<GeoFeature>): ImportResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { ImportResult.Success(block(it)) }
                ?: ImportResult.Failure("Could not open file")
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "Import failed")
        }
    }

    // ------------------------------------------------------------ map import

    /**
     * Import a georeferenced map image. Georeferencing resolution mirrors the
     * reference app's pipeline priorities: an ESRI world file (.jgw/.pgw/.tfw)
     * takes precedence; otherwise the map imports uncalibrated and corner
     * calibration can be applied later from the map details screen.
     */
    fun importMapImage(context: Context, uri: Uri, name: String, collectionId: String?): MapImportResult {
        if (!AppRepository.canImportMoreMaps()) {
            return MapImportResult.GateBlocked(
                "Your current plan limits custom map imports. Upgrade to Plus for unlimited imports.",
            )
        }
        val dir = File(context.filesDir, "maps").apply { mkdirs() }
        val ext = uri.lastPathSegment?.substringAfterLast('.', "img") ?: "img"
        val dest = File(dir, "${AppRepository.newId()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            ?: return MapImportResult.Failure("Could not read image")

        val georef: WorldFile.Georef? = WorldFile.readFor(dest)
        val map = OfftrailMap(
            id = AppRepository.newId(),
            name = name,
            collectionId = collectionId,
            filePath = dest.absolutePath,
            sizeBytes = dest.length(),
            importedAtEpochSec = System.currentTimeMillis() / 1000,
            topLeftLat = georef?.topLeftLat,
            topLeftLon = georef?.topLeftLon,
            bottomRightLat = georef?.bottomRightLat,
            bottomRightLon = georef?.bottomRightLon,
            isGeoreferenced = georef != null,
        )
        val added = AppRepository.addMap(map)
        if (!added) {
            dest.delete()
            return MapImportResult.GateBlocked("Import limit reached for the Free edition.")
        }
        return MapImportResult.Success(map)
    }
}

/** World-file (TFW/JGW/PGW) reader for image georeferencing. */
object WorldFile {

    data class Georef(val topLeftLat: Double, val topLeftLon: Double, val bottomRightLat: Double, val bottomRightLon: Double)

    fun readFor(imageFile: File): Georef? {
        val base = imageFile.nameWithoutExtension
        val worldExt = when (imageFile.extension.lowercase()) {
            "jpg", "jpeg" -> "jgw"
            "png" -> "pgw"
            "tif", "tiff" -> "tfw"
            else -> return null
        }
        val world = File(imageFile.parentFile, "$base.$worldExt")
        if (!world.exists()) return null
        return runCatching {
            val lines = world.readLines().map { it.trim().toDouble() }
            if (lines.size < 6) return null
            // World file: A (x-scale), D, B (skew), E (negative y-scale), C (x of top-left center), F (y of top-left center)
            val a = lines[0]; val e = lines[3]; val c = lines[4]; val f = lines[5]
            val size = imageSizePixels(imageFile) ?: return null
            val lonLeft = c - a / 2
            val lonRight = lonLeft + a * size.first
            val latTop = f + e / 2
            val latBottom = latTop + e * size.second
            Georef(latTop, lonLeft, latBottom, lonRight)
        }.getOrNull()
    }

    /** Image dimension probe without decoding the full bitmap. */
    fun imageSizePixels(file: File): Pair<Int, Int>? {
        return runCatching {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) null else Pair(opts.outWidth, opts.outHeight)
        }.getOrNull()
    }
}
