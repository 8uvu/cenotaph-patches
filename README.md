# Cenotaph Patches

Morphe patches for Instagram: chat wallpaper, chat tint, bubble corners, local font, DM streaks and a
theme picker. Self-contained `app.epitaph.*` runtime, so it applies next to Piko without either bundle
overwriting the other.

**Add to Morphe:** https://morphe.software/add-source?github=8uvu/cenotaph-patches

## Target

`com.instagram.android` **447.0.0.55.81** (versionCode **385311895**, arm64-v8a, 480dpi APK bundle) —
the same build Piko 3.10.0 targets. `439.0.0.37.89` (384510792) is also listed.

Bring your own APKM; this repo never hosts Instagram's APK.

## Patches

| Patch | Does |
|---|---|
| **Thread anchor** (required) | Hooks the DM thread's real root-view method and hands the screen to the other patches |
| **Runtime** (required) | Injects the one `onCreate` call that installs the runtime |
| **Chat wallpaper** | Per-thread image, blur, rotation pool, dim, solid tint, hide Seen-by |
| **Colour map** | Recolours a screen by `source=>target` hex pairs |
| **Bubble corners** | Corner radius on already-rounded chat bubbles |
| **Local chat font** | Typeface from a file path on your phone |
| **DM streaks** | Streak badge in the inbox row and thread header |
| **Theme picker** | Adds the `+` button to the chat theme preview |
| **Settings app** | Standalone Cenotaph Settings activity |
| **Category in Piko Settings** | Same settings inside Piko's settings home |

All ten are on by default. Each feature patch injects its own registration call, so unticking one in
Morphe actually disables that feature instead of leaving dead code installed. Values live in
**Cenotaph Settings** and apply immediately.

## Build and check

```bash
./gradlew :patches:buildAndroid          # -> patches/build/libs/patches-<version>.mpp
bash scripts/verify-bundle.sh "Instagram app/<file>.apkm"   # applies it and checks the output
```

`scripts/verify-bundle.sh` patches the APKM and then inspects the result: patch count, per-feature
injected calls, the settings activity in the manifest, and that removed features left nothing behind.

## Notes

- Local only: no network, no telemetry. Nothing here changes server-side behaviour.
- Modified clients are against Instagram's terms.
- GPLv3 — see [LICENSE](LICENSE).
