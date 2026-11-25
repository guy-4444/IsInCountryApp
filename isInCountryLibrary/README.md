# isInCountryLibrary

Offline, zero-dependency Android library for point-in-country and sea/ocean detection using GeoJSON country polygons (`assets/countries.json`).

## Features

- **Offline & Self-Contained**: Bundles Natural Earth country polygons in `src/main/assets/countries.json` with no network calls or third-party JSON/geometry dependencies.
- **Simple Kotlin & Java API**: Top-level Kotlin functions (`isInCountry`, `inWhichCountry`, `isInSea`) plus `@JvmStatic` methods on `CountryLocator`.
- **Zero-Config Initialization**: Automatically registers the Android `Context` at startup via `CountryLocatorInitProvider`.
- **Fast Lookups**: Parses coordinates into primitive `DoubleArray` rings with per-feature and per-ring bounding boxes for fast ray-casting checks.
- **Accurate Topology**:
  - Supports both `Polygon` and `MultiPolygon` geometries.
  - Handles interior polygon holes/enclaves (e.g., Lesotho inside South Africa).
  - Resolves Natural Earth `-99` ISO code edge cases (e.g., France `FR`/`FRA`, Norway `NO`/`NOR`, Kosovo `XK`/`XKX`) and common aliases (e.g., `UK` for `GB`).
  - Validates coordinate bounds (`lat` in `-90.0..90.0`, `lon` in `-180.0..180.0`, finite values).

---

## Setup

### JitPack

Add the JitPack repository to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency in your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.guy-4444:IsInCountryApp:1.00.03")
}
```

No manual initialization is required—the library automatically initializes itself on app startup.

---

## Usage

### Kotlin

```kotlin
import com.guy.isincountrylibrary.CountryLocator
import com.guy.isincountrylibrary.inWhichCountry
import com.guy.isincountrylibrary.isInCountry
import com.guy.isincountrylibrary.isInSea

// Check if a coordinate is inside a specific country (ISO alpha-2 or alpha-3, case-insensitive)
val inIsrael = isInCountry(32.0853, 34.7818, "IL")   // true
val inIsrael3 = isInCountry(32.0853, 34.7818, "ISR") // true

// Find which country contains a coordinate (returns ISO alpha-2 code, or null if in sea/invalid)
val countryCode = inWhichCountry(48.8566, 2.3522)    // "FR"
val oceanCode = inWhichCountry(0.0, -30.0)           // null

// Check if a coordinate is in the sea/ocean (valid Earth coordinate outside all country polygons)
val atSea = isInSea(32.0853, 34.0000)                // true

// Get full country details (name, isoA2, isoA3)
val info = CountryLocator.getCountryInfo(32.0853, 34.7818)
// CountryInfo(name="Israel", isoA2="IL", isoA3="ISR")
```

### Java

```java
import com.guy.isincountrylibrary.CountryInfo;
import com.guy.isincountrylibrary.CountryLocator;

boolean inIsrael = CountryLocator.isInCountry(32.0853, 34.7818, "IL");
String isoCode = CountryLocator.inWhichCountry(48.8566, 2.3522); // "FR"
boolean atSea = CountryLocator.isInSea(0.0, -30.0);              // true
CountryInfo info = CountryLocator.getCountryInfo(32.0853, 34.7818);
```

---

## API Reference

| Function | Return Type | Description |
| :--- | :--- | :--- |
| `isInCountry(lat, lon, isoCode)` | `Boolean` | Returns `true` if `(lat, lon)` lies inside the country matching `isoCode` (ISO alpha-2, alpha-3, or numeric/admin code, case-insensitive). Returns `false` for invalid coordinates. |
| `inWhichCountry(lat, lon)` | `String?` | Returns the ISO alpha-2 code (e.g. `"IL"`, `"US"`, `"FR"`) of the country containing `(lat, lon)`, or `null` if in the sea/ocean or invalid. |
| `isInSea(lat, lon)` | `Boolean` | Returns `true` if `(lat, lon)` is a valid Earth coordinate (`-90..90`, `-180..180`) not inside any country polygon. |
| `CountryLocator.getCountryInfo(lat, lon)` | `CountryInfo?` | Returns `CountryInfo(name, isoA2, isoA3)` for `(lat, lon)`, or `null` if in the sea or invalid. |
| `CountryLocator.loadFromStream(inputStream)` | `Unit` | Loads polygons from a GeoJSON `InputStream` (e.g. for unit tests or custom sources). |
| `CountryLocator.loadFromJson(json)` | `Unit` | Loads polygons from a GeoJSON `String`. |

---

## Requirements

- **minSdk**: 24
- **compileSdk**: 36
- **Java**: 11

## Testing

Run the library's unit tests with:

```bash
./gradlew :isInCountryLibrary:testDebugUnitTest
```
