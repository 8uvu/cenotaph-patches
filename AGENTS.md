# Cenotaph Patches — working rules

Morphe patch repository for Instagram. The pipeline and reference material come from
`morphe-ai/` (Paresh-Maheshwari/morphe-ai, GPL-3.0); the on-demand skills are installed in
`.agents/skills/` (`create-patch`, `recon-apk`, `decompile-apk`, `find-patch-targets`,
`write-patch`, `validate-patch`, `test-on-device`, `submit-patch`) with the upstream references
under `.agents/skills/morphe-patch-creator/references/`.

## Pipeline

    RECON -> DECOMPILE -> HUNT -> WRITE -> BUILD -> VALIDATE

A stage is only done when its artifact exists. Java explains behaviour; **smali is the source of
truth** for descriptors, flags, registers and patch points. A fingerprint without recorded smali
evidence is not ready to ship.

## Target

`447.0.0.55.81` (`com.instagram.android`, arm64-v8a). Local copy for analysis:
`screenshots/base-447.apk`. The 439 APKM in `Instagram app/` is kept only as a fallback target.

Analysis artifacts live in `analysis/instagram447/`:

    dex/          classes*.dex extracted from the APK
    smali/        baksmali output, one directory per dex
    notes/        recon.md, target notes
    decompile.sh  re-runs the disassembly

## Local toolchain (no network installs needed)

| Tool | Path |
|---|---|
| baksmali / smali 3.0.10 | `java -cp "tools/smali/lib/*" com.android.tools.smali.baksmali.Main` |
| jadx 1.5.6 | `tools/jadx/bin/jadx.bat` (`tools/jadx/lib/jadx-1.5.6-all.jar`) |
| aapt2 | `tools/aapt2/aapt2.exe` |
| apktool | `tools/apktool.jar` |
| dex2jar | `tools/dex2jar/` |
| ripgrep | `tools/rg/` |
| morphe-cli | `tools/morphe-cli/jdk-21.0.12.1+1-jre/bin/java.exe -jar tools/morphe-cli/cli.jar` |
| JDK 21 | `tools/morphe-cli/jdk-21.0.12.1+1-jre/` |

## Patch rules

1. **Every patch hooks real app code.** A patch whose only injection is
   `Application.onCreate` does not count as a feature patch — it is a service locator that has to
   guess at runtime. Find the method that actually renders the thing being changed and anchor
   there.
2. **No silent failure.** `hardAnchor` when the anchor is the only path to the feature, so Morphe
   reports a failure instead of installing an inert patch. `softAnchor` only when a second driver
   exists, and the reason is written in a comment.
3. **One feature, one registration call.** Feature patches inject their own no-argument
   `invoke-static {}` so leaving a patch unticked genuinely disables it.
4. **No `invoke-static {p0}`** in Instagram methods: `p0` sits above v15 and non-range invokes
   cannot address it. Use an empty register list or the `/range` form.
5. **Record evidence** in the patch KDoc: dex, class, method signature, match rationale.
6. Extension package stays `app.epitaph.extension.custom` so it never collides with Piko's dex.
7. No Piko compile-time imports. Anything shared with Piko goes through reflection or literal
   `SharedPreferences` keys.

## Build and validate

    ./gradlew :patches:buildAndroid :patches:generatePatchesList --console=plain
    ./scripts/verify-bundle.sh            # patch the 439 APKM and assert every symbol

Patching directly:

    tools/morphe-cli/jdk-21.0.12.1+1-jre/bin/java.exe -jar tools/morphe-cli/cli.jar \
      patch -p patches/build/libs/patches-<ver>.mpp -o out.apk \
      --continue-on-error --unsigned "<apk>"

Build success proves compilation. `list-patches` proves registration. Applying the bundle proves
compatibility and fingerprint matching. None of them prove the feature works on screen — only a
device does, and there is no device attached here.

## Git

Conventional commits (`feat:` minor, `fix:` patch, `chore:` none). Do not rewrite published
history or force-push without explicit approval.
