# YS Gallery

A fast, private photo and video gallery for Android with a Samsung One UI-style interface. Everything
runs on the device: the app has no internet permission, no ads, no tracking and no account.

This is a personal fork of [Prism Gallery](https://github.com/yeasin0423/PrismGallery), rebuilt around a
photo editor and trimmed to a single design language (One UI) and a single language (English).

## Features

- **One UI look**: AMOLED-friendly dark and light palettes, grouped cards, floating frosted bars and
  smooth page transitions
- Photos, videos and GIFs from internal storage and the SD card
- Albums: create, rename, hide, sub-albums, and move media or whole albums between storage volumes
- Favorites and trash
- Deep zoom that stays readable on huge photos and very long scroll screenshots
- Storage overview for internal storage and the SD card
- "Open with" support for photos and videos from other apps
- Adjustable graphics quality for older or low-memory devices
- English only, by design

## Editor

The editor opens from any photo (videos and GIFs are intentionally not editable yet) and works in
five tabs:

- **Crop** – free or fixed ratios, rotate a quarter turn either way, mirror horizontally or vertically
- **Adjust** – brightness, contrast, saturation, warmth, tint, black point, white point and highlights
- **Filters** – 27 live presets, including retro film and tape looks, a Bokeh switch that lays soft
  out-of-focus lights over the photo, and frames (white, black, double line, polaroid)
- **Draw** – brush, eraser, blur brush and mosaic brush, sharing one width slider, with stroke or
  circular patch shapes
- **Text** – multiple draggable, resizable, rotatable labels in six font styles, typed in a floating
  writing bar with a tick

Also included: undo, redo and reset; a before/after compare button above the photo (tap for a draggable
split view, hold to peek at the original); pinch zoom and pan; a live ring showing the brush, eraser,
blur or mosaic size; and a warning when closing with unsaved edits. Edits are saved as a copy by
default, with a confirmed overwrite of the original as the alternative.

Every effect is applied by the same pipeline in both the live preview and the export: transform, crop,
colour matrix, blur/mosaic patches, bokeh, ink, labels, then the frame.

## Privacy

Gallery has no internet permission, so it cannot send anything anywhere. It only asks for access to your
photos and videos. Settings and edits stay on the device.

## Requirements

Android 10 (API 29) or newer.

## Download

Builds are published on the
[releases page](https://github.com/yeasin0423/YSGallery/releases). Release APKs are unsigned unless a
local `keystore.properties` is present, so treat CI artifacts as test builds.

## Building

Requirements: JDK 17 or newer and the Android SDK (compile SDK 34).

```sh
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. Without a signing key the release APK is
unsigned. To sign it, create a git-ignored `keystore.properties` in the project root:

```properties
storeFile=/path/to/your-release-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

### Gradle in CI

There are two **manual** workflows (`workflow_dispatch`); a normal push does not start a build:

- **Build release APK** – the normal build. It uses the Gradle wrapper (`./gradlew`), which downloads
  Gradle 8.9 from `https://services.gradle.org/distributions/gradle-8.9-bin.zip` and verifies its
  SHA-256 (`distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties`).
- **Build release APK (mirror)** – the fallback for when services.gradle.org is down. It downloads the
  identical archive from this repository's `v2.7-b12` release, checks the same SHA-256 and runs that
  `gradle` directly.

Both upload the unsigned APK as an artifact. The Gradle archive is not stored in the repository; older
builds that committed it in three parts were purged from the history so clones stay small.

## Disclaimer

Gallery is an independent project and is not affiliated with, endorsed by or connected to Samsung. The
name is only used to describe the visual style the design is inspired by.

## License

Copyright (C) 2026 Soretake

This program is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License as published by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
[GNU General Public License](LICENSE) for more details.
