# YT-DLP Commander (rebuilt)

A fresh rebuild of your downloader app - same `com.blue.ytdlpcommander` package,
built from scratch since the original source was gone. Kotlin + Jetpack Compose,
using the [youtubedl-android](https://github.com/yausername/youtubedl-android)
library, which bundles yt-dlp, Python, and FFmpeg as native libraries under the hood.

## Important: installing over your existing app

Same package ID, but this build is signed with a fresh debug key, not
whatever key the original APK used. Android blocks installing a package
over an existing one with a *different* signature - you'll likely see
**"App not installed - conflicts with an existing package."**

Fix: **uninstall the old YT-DLP Commander first**, then install this one.
Your download history from the old app won't carry over (it wasn't
recoverable from the APK either), but everything from here on is tracked
by the new History screen.

## Building it (same flow as before)

1. Unzip, upload the contents to your `github.com/ajcries/...` repo (or a new one)
2. The included `.github/workflows/build.yml` builds a debug APK automatically on push
3. Download it from the Actions run's **Artifacts** section
4. Uninstall the old app, install this one

I could not compile or run this here (no Android SDK in this environment) -
written carefully against the documented APIs, but do a first real test
before relying on it, especially the download engine itself.

## What's here, feature by feature

**1. Runs in the background** - `DownloadService` is a proper foreground
service. Downloads keep running even if you leave the app or lock your
phone, same as the original.

**2. Fun, friendlier UI** - Compose Material 3, a colorful gradient header
on Home, chip-based quality picker, card-based lists everywhere instead of
plain rows.

**3. Clickable history links** - every entry in **History** shows its
original source URL as an underlined link. Tapping it always re-queues a
fresh download from that URL - exactly for the case where you (or Android)
deleted the saved file. The row also shows "File no longer available" and
swaps the action button to a redownload icon automatically when it detects
the file is gone.

**4. Bottom navigation** - Home / Downloads / History / Settings, standard
Android nav-bar pattern instead of everything crammed onto one screen.

**5. Parallel downloads** - `DownloadService` runs a `Semaphore`-limited
coroutine pool; the limit is adjustable in Settings (1-5 at once). Queue up
five links from five different sites and they'll run together up to that cap.

**6. Live + completion notifications** - a per-download notification with a
progress bar and ETA while it runs (all under one low-priority "Downloads"
channel so they don't spam), a "Download complete" (or "failed")
notification when it's done that opens the file directly on tap, and an
aggregate "N downloads in progress" notification anchoring the foreground service.

**7. The floating quick-download tab** - toggle it on in Settings. It asks
for the "draw over other apps" permission (same as the screen recorder's
overlay bar), then shows a small tab docked to the right edge of the
screen, over any app, draggable up/down. Tap it (without dragging) to pop
open a small panel with a paste URL field (auto-filled from clipboard if
it looks like a link) and a Download button - submitting sends it straight
to the background queue and collapses the panel. There's also a "Turn off"
action right on its notification.

**Bonus - share-to-download:** tap "Share" on a video in YouTube/TikTok/
Instagram/etc and pick this app from the share sheet - it grabs the link
and queues the download immediately, no need to open the app or use the tab.

## v2 update - the big one

Everything from your latest round of feedback:

- **Modes** (Video / Audio / Subtitles / Analyze) - each builds different
  yt-dlp options. Analyze runs `--dump-json` (no download) and lists every
  available format with resolution/size; tap one to download that exact
  format.
- **Advanced Settings dialog** (the "Adv." button on Home): speed limit
  (`--limit-rate`), free-form extra yt-dlp flags applied to every download,
  cookies.txt import (for login-gated content - export one from your
  browser with an extension, import it here), and a real **"Update yt-dlp"**
  button that calls the library's actual `updateYoutubeDL()` self-update,
  not a placeholder.
- **Save to** a custom folder: tap "Change" on Home to pick *any* folder
  via Android's folder picker (including SD card) - the permission is
  persisted, so it keeps saving there. Leave it on "Auto" to keep using the
  public Movies/Music/Downloads collections like before.
- **Share** button (Home, and per-row in History) to send a finished file
  to any other app via the system share sheet.
- **Floating tab redesign**: tap anywhere outside the popup panel to
  collapse it (not just the tab itself) - uses `FLAG_WATCH_OUTSIDE_TOUCH`.
  Drag the tab down to the bottom of the screen and it tints red and turns
  off when you release, chat-head style.
- **Quick Settings Tile** for the floating tab - `QuickTabTileService` lets
  you toggle it straight from the notification shade drop-down, no need to
  open the app first.
- **In-app activity log** on Home, terminal-style, showing queue/progress/
  complete/fail events as they happen.
- **Redesigned UI**: a dedicated dark "commander" theme (electric violet /
  neon accents, not generic Material blue) with card-sectioned layout
  (URL / Mode / Quality / Save To / Log), monospace type for URLs and log
  lines.

### Known simplifications in this round
- Custom yt-dlp flags are split on whitespace - flags needing a quoted
  argument *with* spaces inside it (rare) won't parse correctly.
- Analyze only handles a single video/URL, not playlists.
- "Save to a custom folder" copies the file there after yt-dlp finishes
  writing it to a private staging area first (yt-dlp itself can't write
  directly into an arbitrary SAF folder) - works fine, just means the file
  briefly exists in two places during the copy, cleaned up right after.
- Wi-Fi-only toggle is still stored but not yet enforced (same note as before).

## Fix log

- **Build failure: `Could not find com.github.yausername.youtubedl-android:library:0.17.3`**
  The original JitPack coordinate for this library is dead - maintenance
  moved to a new maintainer publishing to Maven Central under
  `io.github.junkfood02.youtubedl-android` (current version `0.18.1`).
  Fixed in `app/build.gradle.kts` and `settings.gradle.kts` (JitPack repo
  removed, no longer needed since Maven Central already covers it).



- **Getting the final filename/title**: the engine uses yt-dlp's `--print`
  hooks (`%(title)s` and `after_move:filepath`) to learn the resulting file
  path and title from the process output. This is the documented, standard
  way to do it, but I haven't been able to verify the exact output parsing
  on a real device/real yt-dlp run. If titles or file detection come out
  wrong, check `DownloadEngine.kt` - the parsing logic is isolated there.
- **Parallel download limit** is read once when a batch of downloads
  starts; changing the Settings slider mid-download doesn't reshuffle
  already-running jobs, only future ones.
- **Wi-Fi only** toggle is stored but not yet wired up to actually block
  downloads on mobile data - that check would go at the top of
  `DownloadService.enqueue()`. Small addition if you want it enforced.
- **First run is slower**: extracting the bundled Python/yt-dlp/FFmpeg
  binaries takes a few seconds the very first time the app launches. The
  Home screen shows "Preparing engine…" on the download button until that
  finishes.
- No in-app video quality *preview* before downloading (thumbnail, exact
  resolution list) - quality is picked from fixed presets (Best/1080p/720p/
  480p/Audio-only MP3) rather than querying each link's actually-available
  formats. Doable as a follow-up (yt-dlp's `-j`/`--dump-json` metadata call)
  but adds a network round-trip before every download starts.
- A quick word on the obvious: yt-dlp is legitimate, widely-used open
  source software, but downloading copyrighted video without the rights
  holder's permission can still violate a platform's terms of service or
  copyright law depending on what and where - that's on how the app gets
  used, same as it was with the original.
