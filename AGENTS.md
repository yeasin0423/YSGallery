# AGENTS.md

Guidance for agents working in this repository.

## What this app is

**Gallery** (`com.gallery.app`) is a private, offline Android photo/video gallery. It has **no internet
permission**, no ads, no tracking and no account. Keep it that way: never add network permissions,
analytics, crash reporting or telemetry.

The UI is **Samsung One UI styling only** and **English only**. The upstream project shipped four design
languages (Samsung One UI, Google Gemini, iOS Liquid Glass, Xiaomi HyperOS) and English/German strings;
this fork intentionally keeps only One UI and English.

This fork is built around its photo editor: crop/rotate/mirror, adjust, filters, bokeh, drawing
(brush, eraser, blur, mosaic), text labels with fonts, frames, before/after comparison and a
floating text bar. Videos and GIFs are intentionally not editable (`MediaItem.isImage` gates the entry
point); do not enable it without a full video pipeline.

## Repository layout

```
.
├── app/
│   ├── build.gradle.kts          # namespace/applicationId = com.gallery.app
│   ├── src/main/
│   │   ├── AndroidManifest.xml   # one launcher alias: .LauncherOneUi
│   │   ├── java/com/gallery/app/
│   │   │   ├── GalleryApplication.kt   # Application, graphics-quality tiers, Coil setup
│   │   │   ├── MainActivity.kt         # app shell, navigation, theme
│   │   │   ├── data/                   # MediaStore repository, thumbnails
│   │   │   ├── model/                  # MediaItem, AlbumItem, sorts
│   │   │   ├── service/                # live wallpaper, move-media service
│   │   │   ├── ui/
│   │   │   │   ├── components/         # shared composables (bars, sheets, grid items)
│   │   │   │   ├── design/             # design-system primitives, motion, overlays
│   │   │   │   ├── editor/             # photo editor (see below)
│   │   │   │   ├── i18n/Strings.kt     # English strings (only language)
│   │   │   │   ├── screens/            # feature screens + One UI settings pages
│   │   │   │   ├── theme/              # palettes, shapes, ThemeState
│   │   │   │   └── viewmodel/          # GalleryViewModel
│   │   │   └── util/                   # launcher icon, wallpaper accents, helpers
│   │   └── res/                        # strings, themes, launcher icons
│   └── proguard-rules.pro
├── build.gradle.kts              # pins R8 8.8.34 for reproducible builds
├── settings.gradle.kts           # rootProject.name = "Gallery"
├── gradle/wrapper/               # Gradle 8.9
├── fastlane/metadata/android/en-US/   # store listing (English only)
└── .github/workflows/build-release.yml
```

## Editor structure

`ui/editor/MediaEditorScreen.kt` holds the screen state, gestures, preview canvas and tool panels;
`MediaEditor.kt` holds loading, decoding and the export pipeline. Everything the preview draws must be
reproduced by the exporter, in this order:

1. mirror, rotate, crop
2. colour matrix (adjustments combined with the filter)
3. blur and mosaic patches (`EditorBlur.kt`)
4. bokeh lights (`EditorBokeh.kt`)
5. freehand strokes (`EditorDraw.kt`)
6. text labels (`EditorText.kt`)
7. frame (`EditorFrame.kt`)

- `EditSpec` is the single source of truth passed to `saveImage`/`saveVideo`; add new effects there and
  in `EditorSnapshot` (undo), `applySnapshot`, `reset` and the `changed` check.
- Strokes, labels, patches and orbs store normalized coordinates so rotation, mirroring and cropping
  carry them along.
- The before/after original is the current frame with adjustments, filters, bokeh, ink, patches,
  labels and the frame removed; rotation/mirror stay so both halves line up.
- Text is typed in the floating writing bar, not inside the box; the tick finishes the edit.
- Selecting a lead photo opens the editor through `MediaDetailScreen`; closing it with unsaved changes
  must go through `requestClose`.

## Build and verification

There is no local JDK/Android SDK guarantee in every environment. Prefer CI as the build check:

```sh
./gradlew assembleRelease --no-daemon
```

- CI runs **only** via manual `workflow_dispatch` (Ubuntu 24.04, Temurin JDK 21); a normal push does
  **not** start a build. Two workflows exist:
  - `.github/workflows/build-release.yml` — the normal build, via the Gradle wrapper, which downloads
    Gradle 8.9 from `services.gradle.org` and verifies its SHA-256.
  - `.github/workflows/build-release-mirror.yml` — the fallback; downloads the same archive from the
    repository's `v2.7-b12` release when the normal download fails, verifies the same SHA-256 and runs
    that `gradle` directly.
  Never commit the Gradle distribution to the repository again; use the mirror workflow instead.
- The workflow uploads `app-release-unsigned.apk` as `Gallery-unsigned-<ref>`.
- The release APK is unsigned unless a git-ignored `keystore.properties` exists.
- Keep the R8 `8.8.34` pin in the root `build.gradle.kts`; changing it breaks the reproducible build.
- Kotlin/JVM target is 17, `compileSdk`/`targetSdk` 34, `minSdk` 29.

## Current styling/translation state (important)

- `DesignLanguage` has a single value, `SAMSUNG_ONEUI`. The Gemini, iOS Liquid Glass and HyperOS
  screens, helpers and enum values were deleted; only the One UI paths exist. **Do not add a design
  picker or reintroduce those styles.**
- `AppLanguage` is reduced to `ENGLISH`; `LanguageState.language`/`locale` are constants. `strings`
  always returns `EnglishStrings`. Do not reintroduce a language picker or `GermanStrings`.
- The manifest has a single launcher alias, `.LauncherOneUi`. `LauncherIcon.apply` also disables the old
  Gemini/iOS/HyperOS aliases from previous builds.
- Launcher icons are plain PNGs in `res/mipmap-*dpi/` (`ic_launcher`, `ic_launcher_oneui`) rendered from
  the repository's `latest.svg`. There are no adaptive-icon XMLs; keep it that way unless the artwork
  is reworked into separate background/foreground layers.
- The settings hub shows only section titles. Counts, storage summaries and version numbers belong on
  the pushed pages, not in the list rows.
- Legacy `amoled_gallery_*` SharedPreferences names are kept intentionally so existing settings survive.
- Internal names `AmoledBlack` and `AmoledVideoPlayer` remain for historical reasons; renaming them is
  optional churn.

## Editing conventions

- **Bump the version on every commit.** Increase `versionCode` by 1 and update `versionName` in
  `app/build.gradle.kts` with each change that is committed, so every build is distinguishable.
  The name is the code with a dot before the last digit: 64 is `6.4`, 65 is `6.5`, 77 is `7.7`.
  Do not use multi-digit minor versions such as `6.12`.
- Keep changes split into focused commits; the owner builds each one on CI so a regression is easy to
  bisect.
- Match the surrounding Compose style (4-space indent, trailing commas, one composable per concern).
- Read user-facing text from `strings` / `AppStrings`; do not hardcode UI strings in composables.
- Keep the One UI look: AMOLED black background, compact top titles, floating frosted bars, grouped
  cards with coloured circle icons.
- All Kotlin files live under `app/src/main/java/com/gallery/app/` and their `package` line must match
  the directory.
- Do not commit `keystore.properties`, signing keys, `local.properties` or build output.
- Saving defaults to a copy; overwriting the original must stay behind an explicit confirmation.

## Testing notes

- There are no unit/instrumentation tests in this fork; CI compilation is the primary gate, followed by
  manual installs of the artifact.
- When changing resources, verify `R.*` references still resolve and that removed drawables/mipmaps are
  not referenced from the manifest or Kotlin.
- For editor changes, check both the live preview and the exported file; a mismatch usually means the
  preview and `MediaEditor.renderImage` drifted apart.
