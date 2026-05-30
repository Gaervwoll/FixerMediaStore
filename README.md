# Fixer MediaStore

**Restore photo and video dates from filenames or EXIF into Android Gallery (MediaStore).**

Simple Android app: reads capture date from **file names** or **EXIF**, writes **DATE_TAKEN** to MediaStore so Google Photos, Samsung Gallery, and others sort media correctly.

**Languages:** English (default) · [Русский](README.ru.md)

---

## Features

- Scan a **selected folder** (including SD card) via system folder picker
- Scan the **entire gallery** (all images/videos in MediaStore)
- Progress bar for scan and apply
- Many **filename patterns** (camera, screenshots, WhatsApp, Telegram, Facebook, WeChat, Viber, Signal, …)
- **EXIF fallback** when the name has no date (on by default)
- Collapsible **settings** and **log** panels
- **Preflight check** before apply (MediaStore match, skip already-correct dates)
- Writes MediaStore dates; updates **EXIF** for JPEG when possible

### Date sources (order)

1. **Filename** — camera, messenger, screenshot patterns  
2. **EXIF** (optional) — `DateTimeOriginal` / `DateTime` in JPEG, HEIC, PNG, WebP  

Log shows source as `[pattern|name]` or `[EXIF|EXIF]`.

### Limitations

- No GPS, captions, or albums  
- No EXIF read for **video** (filename only)  
- Files with **no name date and no EXIF** stay skipped (e.g. `file_42.jpg`)  
- Does not use file modification time (wrong after copy)  
- Folder mode only updates files indexed in MediaStore  

### Requirements

- Android 8.0+ (API 26)  
- Photo & video permission  

---

## Usage

1. Install APK or build from source.  
2. Grant **photos & videos** access.  
3. **Choose folder** (optional, for folder scan).  
4. **Scan folder** or **Scan gallery**.  
5. Expand **Log** to review; stats show **from EXIF** and **no date**.  
6. Expand **Settings and actions** → **Check before apply** (recommended).  
7. **Apply** → confirm.  
8. Check sorting in your gallery app.  

**Tip:** use **Scan gallery** for the whole library. Folder scan only fixes files that exist in MediaStore.

---

## Build APK

```powershell
cd FixerMediaStore
.\gradlew.bat assembleDebug
```

Output: `app\build\outputs\apk\debug\app-debug.apk`

Android Studio: **Build → Build APK(s)**. For releases, use a signed **release** build and attach to GitHub Releases.

---

## Supported filename examples

| Example | Pattern | Notes |
|---------|---------|-------|
| `IMG_20240202_080328.jpg` | IMG_yyyyMMdd_HHmmss | Camera / many apps |
| `IMG_20240515_143022.jpg` | IMG_yyyyMMdd_HHmmss | Optional `_SSS` suffix (e.g. Telegram gallery save) |
| `IMG-20220513-WA0024.jpg` | WA_media_seq | WhatsApp; date only → **12:00**; `WA####` is a daily counter, not time |
| `VID-20220513-WA0024.mp4` | WA_media_seq | WhatsApp video |
| `photo_2024-05-15_14-30-22.jpg` | Telegram_photo | Telegram Desktop save time |
| `video_2024-05-15_14-30-22.mp4` | Telegram_video | Telegram Desktop save time |
| `FB_IMG_1715789420123.jpg` | FB_IMG_ms | Facebook (UNIX ms) |
| `Screenshot_2024-05-15-14-30-22.png` | Screenshot | |

See [README.ru.md](README.ru.md) for naming conventions in Russian.

---

## Project structure

| File | Role |
|------|------|
| `MainActivity.kt` | UI, progress, permissions |
| `FilenameDateParser.kt` | Filename date patterns |
| `DateResolver.kt` | Filename → EXIF chain |
| `ExifDateReader.kt` | Read EXIF dates |
| `MediaStoreIndex.kt` | Gallery index for SAF matching |
| `MetadataRestorer.kt` | Write MediaStore + EXIF |

---

## License

MIT
