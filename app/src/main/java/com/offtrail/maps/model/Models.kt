package com.offtrail.maps.model

import kotlinx.serialization.Serializable

@Serializable
data class MapCollection(val id: String, val name: String, val order: Int = 0)

/**
 * An imported map (georeferenced image or document). Mirrors the reference app's
 * map model: lives in a collection, remembers its source (file or store product),
 * and can be active for viewing.
 */
@Serializable
data class OfftrailMap(
    val id: String,
    val name: String,
    val collectionId: String? = null,
    val filePath: String? = null,
    val sourceUrl: String? = null,
    val storeProductId: String? = null,
    val version: Int = 1,
    val sizeBytes: Long = 0,
    val importedAtEpochSec: Long = 0,
    // Georeferencing: world-file style affine (lat/lon of top-left corner + per-pixel scales),
    // or explicit corner lat/lon pairs for corner-based calibration.
    val topLeftLat: Double? = null,
    val topLeftLon: Double? = null,
    val bottomRightLat: Double? = null,
    val bottomRightLon: Double? = null,
    val isGeoreferenced: Boolean = false,
) {
    fun isFromStore(): Boolean = storeProductId != null
}

enum class FeatureType { PLACEMARK, LINE, AREA, TRACK, PHOTO }

@Serializable
data class LatLon(val lat: Double, val lon: Double)

@Serializable
data class TrackPoint(val lat: Double, val lon: Double, val elevationM: Double, val timeEpochMs: Long)

@Serializable
data class GeoFeature(
    val id: String,
    val name: String,
    val type: FeatureType,
    val layerId: String? = null,
    val mapId: String? = null,
    val points: List<LatLon> = emptyList(),
    val trackPoints: List<TrackPoint> = emptyList(),
    val description: String = "",
    val attributes: Map<String, String> = emptyMap(),
    val photoPaths: List<String> = emptyList(),
    val symbolId: String = "pin-default",
    val distanceMeters: Double = 0.0,
    val areaSqm: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val durationSec: Long = 0,
)

/**
 * Layers can be linked across maps (reference: LinkLayersActivity/LinkMapsActivity):
 * features on the layer show on every linked map whose bounds contain them.
 */
@Serializable
data class Layer(val id: String, val name: String, val linkedMapIds: List<String> = emptyList())

enum class GeofenceScope { FEATURE, LAYER, MAP, GLOBAL }

@Serializable
data class Geofence(
    val id: String,
    val scope: GeofenceScope,
    val targetId: String? = null, // featureId / layerId / mapId depending on scope
    val radiusMeters: Double = 50.0,
    val alertOnEnter: Boolean = true,
    val alertOnExit: Boolean = false,
    val globallyActive: Boolean = false, // Pro: active on all maps at once
)

@Serializable
data class SymbolSet(val id: String, val name: String, val builtIn: Boolean = false)

@Serializable
data class MapStoreProduct(
    val id: String,
    val title: String,
    val publisher: String,
    val description: String,
    val priceCents: Long = 0,
    val version: Int = 1,
    val sizeBytes: Long = 0,
    val downloadUrl: String? = null,
)

@Serializable
data class MapStoreAccount(val email: String = "", val name: String = "", val authenticated: Boolean = false)

/** Root document persisted as JSON in app-private storage. */
@Serializable
data class AppData(
    val collections: List<MapCollection> = emptyList(),
    val maps: List<OfftrailMap> = emptyList(),
    val layers: List<Layer> = emptyList(),
    val features: List<GeoFeature> = emptyList(),
    val geofences: List<Geofence> = emptyList(),
    val symbolSets: List<SymbolSet> = listOf(
        SymbolSet("default", "Default symbols", builtIn = true),
        SymbolSet("search-rescue", "Search & Rescue", builtIn = true),
        SymbolSet("wildfire", "Wildfire", builtIn = true),
    ),
    val purchases: List<String> = emptyList(),
    val account: MapStoreAccount = MapStoreAccount(),
    val showUnlimitedFeatures: Boolean = false,
    val themeMode: String = "system",
    val distanceUnit: String = "METRIC",
    val angleUnit: String = "DEGREES",
    val coordinateFormat: String = "DD_NAMED",
    val showUnlimitedFeaturesPro: Boolean = false,
)
