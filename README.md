# Mutagen

Personal app test lab for Android (Java + XML). Decompile **your own** built APK, inject test code,
edit smali / strings / manifest / assets / identity, then rebuild, sign and install it, all on the phone.

## Build on GitHub Actions
1. Create a repo, upload everything in this folder (keep the `.github` folder).
2. Push to `main`. Open **Actions → Build Mutagen APK**, download the `Mutagen-debug-apk` artifact.
   (No Gradle wrapper needed; the workflow installs Gradle 8.4 + JDK 17.)

## Replace the logo with your own
Overwrite these PNGs (rounded corners baked into the PNG) and push again:
- `app/src/main/res/drawable/logo.png`  (512x512, used in splash + app screens)
- `app/src/main/res/mipmap-*/ic_launcher.png` and `ic_launcher_round.png` (48/72/96/144/192 px)

## Workflow inside the app
1. **Import APK** (or pick an installed app) -> baksmali decompiles every classes*.dex.
2. Open a project and use the tools:
   - **Manifest**: decoded view, replace text, add/remove permissions, set versionCode / minSdk / targetSdk.
   - **Smali code**: browse, edit, search all code, replace all, new / rename / delete class.
   - **Injector**: Dialog, Dialog + link button, Toast, Log, Open URL or Raw smali. Inserted after `super.onCreate`
     of the activity you choose. "Undo last injection" removes it cleanly.
   - **Strings**: edit resources.arsc strings, edit `const-string` values in code, replace everywhere.
   - **Identity**: app name, package id (safe applicationId change or full refactor), version, logo, class rename.
   - **Assets & files**: view, export, replace, add, delete any file inside the APK.
   - **Dex tools**: per-dex class / method counts, add / delete / rename class, re-decompile.
3. **Build & install**: re-assembles smali, repacks (zip-aligned), signs v1+v2+v3, installs.

Signing key is `app/src/main/assets/mutagen.p12` (password `mutagen`). If the original app is installed with a
different signature, uninstall it first or change the package id so both can coexist.

## Notes
- Works on apps you built yourself. Split APKs (app bundles) are not supported; use a single universal APK.
- Injected helper classes live in `com/mutagen/inject/` inside the first smali folder.
