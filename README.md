# Cenotaph Patches

Morphe patches for Instagram, rebuilt from zero as **v1.0.0**.

The rule for this bundle is simple: a patch ships only if its hook lands in
Instagram's own code. Nothing here walks the view tree at runtime and hopes the
right screen is open — that is what the 2.x tree did, and it is why features
could report success and do nothing. The 2.x work is kept on the
`legacy/2.x` branch and the `v2-legacy-final` tag.

## Patches

| Patch | What it does | Anchor |
|---|---|---|
| Cenotaph - Runtime (required) | Lifecycle hook into Instagram's `Application`; features register here | `Application.onCreate` |
| Ceno - Settings app | Adds a launcher entry for Ceno settings | manifest |
| Cenotaph - Thread anchor (required) | Hands the DM thread's root view to the bundle | `LX/6q2->A0Z(Landroid/view/View;)V` in `smali_classes3`, reached from the thread fragment `LX/5Vk` via its `LX/5My` callbacks |
| Cenotaph - Chat wallpaper | Draws your image behind a chat | `LX/6p0->G8p(Ljava/lang/String;Landroid/graphics/Bitmap;)V` — Instagram's own thread background setter, which inflates `immersive_thread_theme_background_stub` and calls `ImageView.setImageBitmap` |

Two of the four are infrastructure; the other two hook real methods. The
settings screen renders from `CenotaphSettings.sections()`, so it can only list
features that exist.

## Install

1. Build the bundle (below) or download `patches-<version>.mpp` from Releases.
2. In Morphe Manager, add the bundle and select the patches you want.
3. Patch an Instagram `447.0.0.55.81` APKM (arm64-v8a).

## Build

    ./gradlew :patches:buildAndroid :patches:generatePatchesList --console=plain

Output: `patches/build/libs/patches-<version>.mpp`.

## Verify

    bash scripts/verify-bundle.sh "Instagram app/<build>.apkm"

The verifier asserts the patch count, that every patch applied, that the
extension carries the symbols the injections call, and that each injected call is
present in the patched dex **and absent from the unpatched one**. That last check
is what distinguishes a hook that runs from a string that merely exists.

## What is not proven

- No device is attached to this repository's workflow, so these hooks are proven
  to attach to real methods, not to render correctly on screen.
- `screenshots/base-447.apk` is itself a previously patched build, so checks that
  assert code is *absent* cannot be trusted on it; use a clean APK for those.
- Public release downloads from GitHub returned 503/504 during development while
  the API download path worked.

## Releases

The 1.x line is cut by hand: push a `vX.Y.Z` tag and attach
`patches-<version>.mpp` to it, then point `patches-bundle.json` at that tag.

The `Release` workflow no longer publishes on every push. semantic-release
derives the next version from the newest tag it can find, and the retained 2.x
tags (`v2.13.1`) still outrank `v1.0.0`, so an automated run would publish
`2.14.0` and repoint `patches-bundle.json` away from this line. The workflow
therefore only compiles on push; publishing needs a manual dispatch with
`publish` ticked.

## Installing over 2.x

Morphe ranks sources by the version in `patches-bundle.json` and never offers a
lower one as an update. If you already added this repo and installed 2.13.x,
remove the source and add it again (or delete it and import the `.mpp` as a
local source) so 1.0.0 is what gets installed.

## If the source will not load

Morphe reads `patches-bundle.json` before it can name the source or download the
bundle. When that step fails the card reads **Unnamed** and reports that the
patch bundle could not be downloaded, even though the files are reachable.

Two addresses work; try them in this order:

1. Add the metadata URL directly instead of the repository:
   `https://raw.githubusercontent.com/8uvu/cenotaph-patches/main/patches-bundle.json`
2. Download `patches-1.0.0.mpp` from Releases and add it as a **Local** source.
   This skips the network entirely and always loads.

If the repository was added while 2.13.x was current, delete the source first: a
source ranked at 2.13.x never auto-updates down to 1.0.0.

## Roadmap

Each of these ships only once its anchor is found in smali:

- `1.1.0` sponsored feed posts
- `1.2.1` Ceno rename and version bump
- `1.2.0` hide typing indicator
- `1.3.0` seen receipts
- `1.4.0` media download
