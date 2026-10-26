package com.offtrail.maps

import com.offtrail.maps.geo.Projections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun `utm round trip keeps meter-level accuracy`() {
        val samples = listOf(
            Pair(46.5197, 8.0),      // Alps
            Pair(-33.8651, 151.2094) // Sydney
        )
        for ((lat, lon) in samples) {
            val utm = Projections.toUtm(lat, lon)
            val (lat2, lon2) = Projections.fromUtm(utm.zone, utm.isSouth, utm.easting, utm.northing)
            assertTrue("lat round trip $lat -> $lat2", kotlin.math.abs(lat - lat2) < 1e-5)
            assertTrue("lon round trip $lon -> $lon2", kotlin.math.abs(lon - lon2) < 1e-5)
        }
    }

    @Test
    fun `mgrs reference point matches expected grid`() {
        // White House (38.8977, -77.0365): UTM 18N 323394E 4307395N (verified by exact
        // meridian-arc integration) → column set S–Z, col 4 = 'V', even-set row offset → 'J'.
        val mgrs = Projections.toMgrs(38.8977, -77.0365)
        assertTrue("got $mgrs", mgrs.startsWith("18S VJ"))
    }

    @Test
    fun `haversine distance is sane for a known pair`() {
        // Paris to London ~344 km.
        val d = Projections.distanceMeters(48.8566, 2.3522, 51.5074, -0.1278)
        assertEquals(343_000.0, d, 5_000.0)
    }

    @Test
    fun `zone and band lookup`() {
        // 8°E falls in zone 32 (6°E–12°E); 46.5°N is band T; -33.86°S is band H.
        assertEquals(32, Projections.zoneFor(8.0))
        assertEquals('T', Projections.bandFor(46.5))
        assertEquals('H', Projections.bandFor(-33.86))
    }
}
