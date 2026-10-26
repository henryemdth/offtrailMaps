package com.offtrail.maps.licensing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.licenseDataStore by preferencesDataStore(name = "offtrail_license")

/**
 * Persists license state in a private DataStore, mirroring the reference app's
 * secure key-value storage used by its native licensing core.
 */
class LicenseStore(private val context: Context) {

    private val key = stringPreferencesKey("license_state_json")
    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<LicenseState> = context.licenseDataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<LicenseState>(it) }.getOrNull() } ?: LicenseState()
    }

    suspend fun current(): LicenseState = state.first()

    suspend fun save(state: LicenseState) {
        context.licenseDataStore.edit { it[key] = json.encodeToString(LicenseState.serializer(), state) }
    }
}
