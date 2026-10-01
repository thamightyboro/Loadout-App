# SWG Loadouts

Android app for keeping track of your Star Wars Galaxies ship components and loadouts.

- **Parts library**: scan a component's examine window with the camera, or pick a screenshot, and the stats fill in automatically. You can check and fix them before saving.
- **Loadouts**: pick a chassis, slot parts from your library, and see total mass against the chassis limit, reactor drain against generation, and how many slots are filled.
- **Backup**: export and import everything as one JSON file (Parts tab → ⋮ menu).

## Getting the APK

Every push to `main` builds the app automatically.

1. Open the repo's **Actions** tab and click the latest green **Build APK** run.
2. Scroll to **Artifacts** and download **SWG-Loadouts-apk** (a zip).
3. Unzip it, copy `app-debug.apk` to your phone and open it. You may need to allow installs from your browser or file manager.

Updates install over the top of the old version, so your saved parts are kept.

## Scanning tips

- Screenshots scan far better than photos of a monitor.
- Move the mouse cursor off the examine window first.
- Scroll the window so every stat line is visible.
