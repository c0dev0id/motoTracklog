# Stack

## Target devices

| Device | Screen | Android |
|---|---|---|
| Motorcycle navigation device (primary) | 7", 1920×1080, landscape-mounted | 14 |
| Phones (secondary) | smallest width < 600dp | 9 or newer |

A GNSS chipset is optional: without one, runs come from a Bluetooth NMEA receiver.

## Android targets

| Setting | Value |
|---|---|
| `minSdk` | 28 (Android 9) |
| `targetSdk` | 36 (Android 16) |
| `compileSdk` | 36 |

## Language and build

| Component | Version |
|---|---|
| Kotlin | 2.0.21 (JVM target 17) |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.13 (Kotlin DSL) |
| JDK | 21 (Temurin in CI) |
| Python | 3, as on `ubuntu-latest` (release notes script) |

## Flavors

| Flavor | Distribution | Self-update | Network access |
|---|---|---|---|
| `github` | GitHub release page and `dev` pre-release; dev builds update from the home.codevoid.de dist store | Yes | `INTERNET` |

A `play` flavor is added on demand.

## Libraries

| Library | Version | Use |
|---|---|---|
| `androidx.appcompat:appcompat` | 1.7.0 | Activities, night mode, `AlertDialog` |
| `com.google.android.material:material` | 1.12.0 | Material3 base theme |
| `androidx.recyclerview:recyclerview` | 1.3.2 | Settings grid, run list |
| `androidx.activity:activity-ktx` | 1.9.3 | `viewModels`, permission result APIs |
| `androidx.core:core-ktx` | 1.17.0 | `FileProvider`, insets, `ServiceCompat`, `LocationManagerCompat`, `LocationListenerCompat`, receiver and intent compat |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.6 | `lifecycleScope`, `repeatOnLifecycle` |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.8.6 | `ViewModel`, `viewModelScope` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 | Run listing, export preview, GPX export, update I/O |

UI is classic Views with view binding; no Compose. Location comes from the platform
`LocationManager` (`GPS_PROVIDER`), not Play Services. GPX is written with
`android.util.Xml`; the updater uses `HttpURLConnection` and `org.json`.

## Tests

| Library | Version |
|---|---|
| JUnit | 4.13.2 |
| Robolectric | 4.16.1 |
| `androidx.test.ext:junit` | 1.3.0 |
| `androidx.test:core` | 1.7.0 |

JVM unit tests in `app/src/test/kotlin`, with Android resources included.

## Assets

- Michroma font (`res/font/michroma.ttf`), SIL Open Font License 1.1, see
  `MICHROMA-LICENSE.txt`.

## CI/CD

GitHub Actions on `ubuntu-latest` with `gradle/actions/setup-gradle@v4`.

| Workflow | Trigger | Runs | Produces |
|---|---|---|---|
| `Check` | push to any branch except `main`, manual | release script tests; lint and unit tests for `githubDebug`; `assembleGithubDebug` | `app-debug` artifact |
| `Build` | push to `main` | `lintGithubRelease`, unit tests, signed `assembleGithubRelease` | `dev` pre-release, dev APK in the dist store's nightly channel |
| `Release` | manual, from `main` | signed `assembleGithubRelease` | `v1.0.x` tag and GitHub release, APK in the dist store's release channel |

The project is not built locally.
