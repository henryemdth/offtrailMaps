package com.offtrail.maps.model

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * JSON-backed app repository. The reference app uses a SQLite database with
 * migrations; this replica keeps the same domain model in a serialized document
 * to stay dependency-light while preserving identical behaviors.
 */
object AppRepository {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private lateinit var file: File

    fun init(context: Context) {
        file = File(context.filesDir, "offtrail_data.json")
        scope.launch {
            val loaded = if (file.exists()) {
                runCatching { json.decodeFromString<AppData>(file.readText()) }.getOrDefault(AppData())
            } else AppData().copy(
                collections = listOf(MapCollection(newId(), "My Maps", 0))
            )
            _data.value = loaded
            save()
        }
    }

    private fun save() {
        scope.launch { file.writeText(json.encodeToString(AppData.serializer(), _data.value)) }
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    fun update(block: (AppData) -> AppData) {
        _data.value = block(_data.value)
        save()
    }

    // ----------------------------------------------------------- collections

    fun addCollection(name: String) = update {
        it.copy(collections = it.collections + MapCollection(newId(), name, it.collections.size))
    }

    fun deleteCollection(id: String) = update {
        it.copy(
            collections = it.collections.filterNot { c -> c.id == id },
            maps = it.maps.filterNot { m -> m.collectionId == id },
        )
    }

    // ------------------------------------------------------------------ maps

    fun importedMapCount(): Int = _data.value.maps.count { !it.isFromStore() }

    fun canImportMoreMaps(): Boolean {
        val max = com.offtrail.maps.licensing.LicenseManager.getMaxImportedMaps()
        return max == com.offtrail.maps.licensing.TierLimits.UNLIMITED || importedMapCount() < max
    }

    fun addMap(map: OfftrailMap): Boolean {
        if (!map.isFromStore() && !canImportMoreMaps()) return false
        update { it.copy(maps = it.maps + map) }
        return true
    }

    fun deleteMap(id: String) = update {
        val map = it.maps.firstOrNull { m -> m.id == id }
        map?.filePath?.let { path -> File(path).delete() }
        it.copy(
            maps = it.maps.filterNot { m -> m.id == id },
            features = it.features.filterNot { f -> f.mapId == id },
        )
    }

    fun mapById(id: String): OfftrailMap? = _data.value.maps.firstOrNull { it.id == id }

    // -------------------------------------------------------------- features

    fun addFeature(feature: GeoFeature) = update { it.copy(features = it.features + feature) }

    fun updateFeature(feature: GeoFeature) = update {
        it.copy(features = it.features.map { f -> if (f.id == feature.id) feature else f })
    }

    fun deleteFeature(id: String) = update {
        it.copy(
            features = it.features.filterNot { f -> f.id == id },
            geofences = it.geofences.filterNot { g -> g.scope == com.offtrail.maps.model.GeofenceScope.FEATURE && g.targetId == id },
        )
    }

    fun featuresForMap(mapId: String?): List<GeoFeature> {
        val d = _data.value
        val linkedLayerIds = d.layers.filter { mapId != null && it.linkedMapIds.contains(mapId) }.map { it.id }
        return d.features.filter { f ->
            (mapId != null && f.mapId == mapId) || (f.layerId != null && f.layerId in linkedLayerIds)
        }
    }

    // ---------------------------------------------------------------- layers

    fun addLayer(name: String) = update { it.copy(layers = it.layers + Layer(newId(), name)) }

    fun linkLayerToMap(layerId: String, mapId: String) = update {
        it.copy(layers = it.layers.map { l ->
            if (l.id == layerId) l.copy(linkedMapIds = (l.linkedMapIds + mapId).distinct()) else l
        })
    }

    // ------------------------------------------------------------ geofences

    fun addGeofence(geofence: Geofence): Boolean {
        val lic = com.offtrail.maps.licensing.LicenseManager
        val count = when (geofence.scope) {
            GeofenceScope.LAYER -> _data.value.geofences.count { it.scope == GeofenceScope.LAYER }
            else -> _data.value.geofences.count { it.scope != GeofenceScope.LAYER }
        }
        val max = if (geofence.scope == GeofenceScope.LAYER) lic.getMaxLayerGeofenceCount() else lic.getMaxIndividualGeofenceCount()
        if (max != com.offtrail.maps.licensing.TierLimits.UNLIMITED && count >= max) return false
        update { it.copy(geofences = it.geofences + geofence) }
        return true
    }

    fun deleteGeofence(id: String) = update { it.copy(geofences = it.geofences.filterNot { g -> g.id == id }) }

    // ------------------------------------------------------------- settings

    fun setSetting(transform: (AppData) -> AppData) = update(transform)

    // ------------------------------------------------------------ map store

    fun recordPurchase(productId: String) = update { it.copy(purchases = (it.purchases + productId).distinct()) }

    fun hasPurchased(productId: String): Boolean = _data.value.purchases.contains(productId)

    fun setAccount(account: MapStoreAccount) = update { it.copy(account = account) }
}
