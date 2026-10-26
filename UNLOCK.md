# Unlocking All Functionality & External-API Audit (Offtrail Maps)

This documents, for our own replica app, (1) every way to enable the full
Free → Plus → Pro behavior, and (2) which external services the app touches and
whether each can be hardcoded/mocked so the app runs **fully unlocked with zero
external APIs**.

> This applies to Offtrail Maps only. It is not a guide to modifying any
> commercial application's licensing.

---

## Part 1 — How to unlock everything

All tier decisions in the app funnel through a single source of truth:
`licensing/LicenseManager.effectiveLicense()`. Every gate reads it:

```
canUseShapeFiles / canUseBNGFormat / canUseChains / canUseMilliRadians /
canUsePlacemarkAveraging / canImportProIcons / canUseLockOrientation / canImportGpx
getMaxIndividualGeofenceCount / getMaxLayerGeofenceCount  (TierLimits, -1 = unlimited)
getMaxImportedMaps / getAllowGlobalGeofences / getAllowMapGeofences
```

…so unlocking the license unlocks **everything**, including the numeric caps
(unlimited geofences, unlimited map imports).

### Option A — zero code changes (recommended first)

Use the app's own flows; they already grant tiers locally:

1. Launch the app → **Store** tab → *Create a free Map Store account* (any name/email).
2. Go to **Settings → Upgrade / Manage license**:
   - tap **Purchase** on the *Offtrail Maps Pro* card (the `MockBillingGateway`
     grants it instantly), or
   - **Start 30 Day Free Trial** (fill the registration form), or
   - register a license code, e.g. `OT-PRO-1234-ABCD` + any email.
3. Done — every gate re-evaluates automatically (`licensingChanged` flow).
   Verify in **Settings**: geofence caps now read "unlimited", BNG/Chains/Mils
   become selectable, GPX/SHP import dialogs stop appearing.

Dev helpers: `LicenseManager.resetHasUsedFreeTrial()` restores the trial button;
`LicenseManager.deregisterLicense()` resets to Free.

### Option B — one-point hardcode (permanent Pro)

Edit `app/src/main/java/com/offtrail/maps/licensing/LicenseManager.kt`, function
`init(...)`, and force a permanent Pro grant on startup:

```kotlin
fun init(store: LicenseStore, billing: BillingGateway) {
    this.store = store
    this.billing = billing
    scope.launch {
        // UNLOCK-ALL: permanent Pro, ignores any previously stored state.
        val s = store.current().copy(
            license = License.PRO,
            type = LicenseType.PURCHASED_ON_DEVICE,
            expiryEpochSec = 4102444800L, // 2100-01-01; avoid 0 so the UI shows an expiry
        )
        store.save(s)
        _state.value = s
        _licensingChanged.emit(Unit)
    }
}
```

Why these values:

- `license = PRO` → every `canUse*` / `isProLicense()` gate passes.
- `expiryEpochSec` far-future → `hasValidLicense()` true forever; the Upgrade
  screen prints an expiry date instead of falling back to the "Free" label
  (`expiryEpochSec = 0` means "never expires" in `resolveExpiry()` but reads as
  Free on the Upgrade screen — cosmetic quirk).
- `type = PURCHASED_ON_DEVICE` → purchase UI stays visible (use
  `REGISTERED` instead if you want the purchase cards hidden, matching the
  original's license-code behavior).

One line also works, at the cost of a stale label on the Upgrade screen:

```kotlin
fun effectiveLicense(s: LicenseState = _state.value): License = License.PRO
```

### Option C — build-type flag (cleanest long-term)

Add a `buildConfigField("boolean", "UNLOCK_ALL", "true")` to a debug build type
and OR it into `effectiveLicense()`. Keeps release builds honest while debug
builds ship fully enabled. (Described, not implemented.)

---

## Part 2 — External API audit

Question: *can the app work without any external API?*
**Yes.** Everything except the basemap imagery is either on-device or already
mocked. With the tile substitution below, the full unlocked behavior runs with
no network at all.

| # | Dependency (original app → replica) | Purpose | Needed for full behavior? | Mock / hardcode strategy | Status |
|---|---|---|---|---|---|
| 1 | Google Maps tiles → **OpenStreetMap raster tiles** (`tile.openstreetmap.org`) | Basemap imagery in Map View | Only for the *visual* basemap — features, tracks, geofences, measure tools all render without it | (a) Bundle an offline **MBTiles** file and feed it to osmdroid's archive tile provider (`MapTileFileArchiveProvider` / `ArchiveFileFactory`); (b) pre-seed the tile cache in `filesDir/osmdroid/tiles`; (c) run "basemap-less" (blank canvas) | **Works today with network; mockable** |
| 2 | Play Services Location (`FusedLocationProviderClient`) → same in replica | GPS fixes for tracking + geofence engine | Yes — but it is an *on-device* service, not a network API; GPS works offline (no Wi-Fi/cell needed) | Devices without GMS: swap to `android.location.LocationManager` behind an interface; testing: inject fixed/mock locations to exercise geofences statically | **Works offline via GPS; swappable** |
| 3 | Google Play Billing → `MockBillingGateway` | Subscriptions (Pro/Plus) | No | Already a local mock; interface ready for a real Play Billing impl | **Already mocked** |
| 4 | `subscription.avenza.com` → local `LicenseStore` (DataStore) | Entitlement state machine | No | State machine runs locally; no server involved | **Already local** |
| 5 | Map Store server → bundled `MapStoreManager.catalog` | Marketplace catalog/purchases | No | Catalog is a hardcoded list; "downloads" register locally | **Already mocked** |
| 6 | What3Words API → stub | W3W coordinate format (Pro) | No | Replica prints `///(requires What3Words API key)`; can be made deterministic: encode lat/lon → pseudo three words, or bundle a tiny offline wordlist | **Already stubbed; mockable** |
| 7 | Firebase (analytics/config/crashlytics), CodePush, Remote Config | Telemetry, OTA, feature flags | No | Absent from the replica; the one Remote-Config behavior (cellular download cap, 200 MB) is a local constant `MapStoreManager.MAX_CELLULAR_DOWNLOAD_MB` | **Not present** |
| 8 | ZXing embedded | QR scan of map URLs | No | Pure on-device library; no network. (The captured URL is only displayed — store downloads are simulated.) | **On-device** |
| 9 | Dropbox SDK, NMEA external GPS, GDAL | Delivery / GPS input / PDF georeferencing | No | Not implemented (README roadmap); none are gating external APIs | **Not present** |

### Hardcoded URLs in the replica

Exactly one network endpoint is referenced: the OSM tile source inside
`ui/screens/MapViewScreen.kt` (`TileSourceFactory.MAPNIK`). Nothing else in the
app performs network I/O.

### Recipe: fully offline, fully unlocked

1. Apply Option B (one-point hardcode) — or Option A if you just want to try it.
2. Drop an `.mbtiles` file covering your area of interest into app storage and
   register it with osmdroid's archive provider (or pre-seed the tile cache), so
   Map View renders without network.
3. Optionally swap the fused-location provider for `LocationManager` if the
   target device has no Google Play Services.

Result: the complete Free→Pro behavior set — imports, tracking, geofences,
coordinate formats, store flows — runs air-gapped.
