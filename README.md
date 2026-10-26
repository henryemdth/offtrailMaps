# Offtrail Maps

A clean-room functional reimplementation of the Avenza Maps Android app's behavior
(Free edition through Pro), built from the reverse-engineering findings in
[`../RE-FINDINGS.md`](../RE-FINDINGS.md).

**Not affiliated with Avenza Maps Inc.** Original code, original branding, open-source
map components. No proprietary code, artwork, or licensed content was copied.

## What replicates the reference behavior

### Licensing — the Free → Plus → Pro system

| Reference behavior | Replica |
|---|---|
| `License { NONE, PLUS, PRO, PRO_FREE_TRIAL }` | `licensing/License.kt` (same enum) |
| `LicenseType { NONE, SUBSCRIPTION_ON_DEVICE, PURCHASED_ON_DEVICE, REGISTERED }` | same enum |
| Licensing core in native lib + `subscription.avenza.com` | `LicenseManager` object with the same state machine, persisted in DataStore |
| Play Billing SKUs `pro.oneyear` / `plus.oneyear` | `BillingGateway` interface + `MockBillingGateway` (swap in Play Billing impl) |
| 30-day Pro free trial, device-bound, `hasUsedFreeTrial` | same (registration form included, hidden after use) |
| License-code registration (`REGISTERED`, hides purchase UI) | same (`OT-PRO-XXXX-XXXX` format check) |
| `LICENSING_CHANGED` broadcast | `licensingChanged` SharedFlow |
| Expiry handling + expiration display | same (`expiry()` epoch-seconds, resolved lazily) |
| `canUseBNGFormat / canUseChains / canUseMilliRadians / canUsePlacemarkAveraging / canUseShapeFiles / canImportProIcons / canUseLockOrientation` | identical function names on `LicenseManager` |
| `getMaxIndividualGeofenceCount / getMaxLayerGeofenceCount` (-1 = unlimited), `getAllowGlobalGeofences / getAllowMapGeofences` | same, values from `TierLimits` (native-computed in the original; configurable here) |
| Gate UX: tier explainer dialog + "Learn More" → upgrade | `GateDialog` / `GateMessages` in `ui/GateDialog.kt` |

### Tier matrix (as implemented)

| Behavior | Free | Plus | Pro / Trial |
|---|---|---|---|
| Map viewing, placemarks/lines/areas/tracks, measure, photos | ✓ | ✓ | ✓ |
| Custom map imports | capped (3) | unlimited | unlimited |
| KML/KMZ/CSV import, GPX/KML/CSV export | ✓ | ✓ | ✓ |
| GPX import | — | ✓ | ✓ |
| Shapefile (SHP) import & export | — | — | ✓ |
| Track → Area conversion | — | — | ✓ |
| Custom symbol sets / Pro icons | — | — | ✓ |
| Chains & milliradian units | — | — | ✓ |
| British National Grid / What3Words formats | — | — | ✓* |
| Lock map orientation | — | — | ✓ |
| Individual geofences | 1 | 5 | unlimited |
| Layer geofences | 0 | 1 | unlimited |
| Global geofences / map-boundary alerts | — | — | ✓ |
| "Show Unlimited Number of Features" | — | — | ✓ |
| 30-day Pro trial (registration form) | ✓ | (hidden once used) | (hidden once used) |

\* What3Words format is present and Pro-gated but requires the proprietary SDK/API key —
it renders `///(requires What3Words API key)` in this build, as documented in RE-FINDINGS §5.

### Other replicated behaviors

- **Map list with collections** (folders), delete, per-map details.
- **Import paths**: file-association activity (`OpenFileWithOfftrailActivity`), in-app picker,
  QR-code scan (ZXing) capturing a map source URL.
- **Georeferenced map import**: world-file (JGW/PGW/TFW) detection at import; corner
  calibration UI on the details screen for un-calibrated images.
- **Track recording**: foreground service with persistent notification
  ("Recording track …" / paused), pause/resume/stop actions, distance + elevation-gain +
  duration stats, saved as a Track feature.
- **Geofencing**: per-feature and per-layer radius alerts, map-boundary and global scopes
  (Pro), enter/exit notifications, tier caps enforced at creation with the upsell dialog.
- **Draw & Measure**: tap vertices → distance/area → save as line/area.
- **Map Store**: bundled catalog, free/paid products, account gate before purchase
  (authorization_required equivalent), version warnings, oversized-cellular-download notice,
  purchase → download → appears in My Maps.
- **Coordinate formats**: DD/DMS/DDM (signed & named), UTM, MGRS, BNG (Pro), What3Words (Pro);
  center readout in the viewer. Distance units incl. chains (Pro), angle units incl. mils (Pro).
- **Settings**: theme (system/light/dark), units, coordinate format, symbol management (Pro),
  feature display cap toggle (Pro), geofence cap summary, database statistics.

## Stack

- Kotlin 2.1 + Jetpack Compose (Material 3), min SDK 26 / target 36
- osmdroid + OpenStreetMap tiles (replaces Google Maps SDK — no API key needed)
- Play Services Location (as in the reference app) for tracking/geofence updates
- DataStore + kotlinx.serialization (replaces the reference's SQLite + native store)
- ZXing embedded for QR import

## Build & run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
gradle assembleDebug        # or open in Android Studio and press Run
```

APK lands in `app/build/outputs/apk/debug/`. First launch: grant location/notification
permissions. Free edition starts immediately; use Settings → Upgrade to exercise the full
tier flow (trial, mock subscriptions, license codes `OT-PRO-1234-ABCD`).

## Deliberate deviations & roadmap

See `../RE-FINDINGS.md` for the full observation ledger. Known gaps (each isolated behind
the same interfaces as the working code):

1. Georeferenced **PDF** import (reference uses GDAL) — GeoTIFF/JPG/PNG + world files only.
2. Dropbox delivery, offline tile packages, NMEA external GPS, placemark averaging UI,
   plot-photos-by-EXIF, layer linking UI (model supports it), navigation arrow overlay,
   CodePush/Remote Config-driven behaviors.
3. `BillingGateway` is a local mock; wire Google Play Billing for real stores.
4. Tier numeric caps in `TierLimits` are documented defaults (the reference computes them
   in native code; production values were not statically observable).
