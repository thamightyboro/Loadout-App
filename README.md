# SWG Loadouts

A free Android companion app for Star Wars Galaxies space players on Restoration. Scan your ship components, rate their rolls, plan loadouts, work out token costs and plan reverse engineering.

## Features

- **Parts**: scan a component's examine window (camera or screenshot) and the stats fill in automatically. Every stat gets a colour rating against all items of the same type and RE level, from Chassis dealer up to Unicorn.
- **Loadouts**: slot parts into a ship and see mass against the chassis limit and reactor drain against generation, with droid command overloads (weapons, engine, reactor, capacitor) applied. Ordnance and countermeasures have their own slots. Export any loadout as a 1920x1080 image to share.
- **Loot**
  - *Scan*: check a part's rolls instantly, see the token odds of beating its best stat on the Space Duty vendor, and get a price check (average token cost x 30 credits, the community rule of thumb).
  - *Odds & tokens*: the chance of a vendor roll meeting your targets (e.g. a level 7 shield under 13,000 mass) and how many tokens that takes on average and to be 50/90/99% sure.
- **RE**: add the parts going into a reverse engineering run (scan, type in or pick from your library) and see the result before you do it: best of each stat plus the level bonus and +1% expertise, with a pre and post rating for every stat.
- **Backup**: export and import everything as one file (Parts tab, menu).

Stats use the original SWG-Source loot tables (uncapped rolls), with Restoration's live values where they differ (e.g. level 9-10 armor mass modifier 0.433).

## Install

1. On your phone, open the [**Releases**](../../releases/latest) page and download the `SWG-Loadouts-vX.X.X.apk` file under **Assets**.
2. Open the downloaded file. If Android asks, allow your browser (or file manager) to install apps, then tap **Install**.

**Updating:** download the newer APK from Releases and install it over the top. Your saved parts, loadouts and projects are kept.

> **Used a test build from before the first release?** Those were signed with a different key, so Android won't update them in place. Back up first (Parts tab > menu > Export), uninstall the old app, install the release, then Import your backup.

## Scanning tips

- Screenshots scan far better than photos of a monitor.
- Move the mouse cursor off the examine window first.
- Scroll the window so every stat line is visible.
- If something reads wrong, tap the part to fix it, or use "Show raw scan text" to see what the scanner saw.

## For maintainers

- Every push to `main` builds a **test APK** (debug-signed), downloadable from the Actions run's Artifacts.
- **Publishing a GitHub Release** (Releases > Draft a new release > choose a tag like `v0.7.3` > Publish) builds the **release APK**, signed with the private key in the repo secrets, and attaches it to that release automatically within a few minutes. Bump `versionCode` and `versionName` in `app/build.gradle.kts` first.
- Signing secrets (Settings > Secrets and variables > Actions): `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Keep an offline backup of the keystore: without it, players can't get updates in place.
- Component data is generated at build time from SWG-Source `dsrc` by `tools/build_components.py`. Restoration-specific values go in `SERVER_OVERRIDES` there.
