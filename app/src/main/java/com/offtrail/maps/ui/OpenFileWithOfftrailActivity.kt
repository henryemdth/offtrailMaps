package com.offtrail.maps.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.offtrail.maps.io.ImportExportManager
import com.offtrail.maps.io.ImportResult
import com.offtrail.maps.model.AppRepository

/**
 * Entry point when another app shares a GPX/KML/KMZ/CSV/SHP file
 * (mirrors OpenFileWithAvenzaMapsActivity). Tier gates apply identically:
 * the user sees the appropriate upgrade dialog next time they open the app.
 */
class OpenFileWithOfftrailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        val result = ImportExportManager.importFeatures(this, uri)
        when (result) {
            is ImportResult.Success -> {
                result.features.forEach { AppRepository.addFeature(it) }
                Toast.makeText(this, "Imported ${result.features.size} features.", Toast.LENGTH_LONG).show()
            }
            is ImportResult.GateBlocked ->
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            is ImportResult.Failure ->
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
