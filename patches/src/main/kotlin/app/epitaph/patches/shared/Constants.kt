package app.epitaph.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

/**
 * Standalone bundle compatibility.
 *
 * 452.1.0.53.64 is the build the anchors are verified against (versionCode read
 * from the APKM's base.apk). Piko 3.11.0 targets the same build, so both bundles
 * can share one unmodified APKM.
 *
 * Bundle 1 (stock):  crimera/piko      -> patches-*.mpp
 * Bundle 2 (this):   you/epitaph-patches -> epitaph-*.mpp
 * Optional:          MorpheApp/morphe-patches universal (Clone app, Disable Play Store updates)
 */
object Constants {
    val COMPATIBILITY_INSTAGRAM =
        Compatibility(
            name = "Instagram",
            packageName = "com.instagram.android",
            apkFileType = ApkFileType.APKM,
            appIconColor = 0xFC483C,
            targets =
                listOf(
                    // Current build, read from the APKM's base.apk
                    // (versionName 452.1.0.53.64 -> versionCode 385811006).
                    AppTarget(
                        version = "452.1.0.53.64",
                        versionCodes =
                            mapOf(
                                SupportedAbi.ARM64_V8A to 385811006,
                            ),
                    ),
                    // Test APKM in this repo. Code read from its own manifest
                    // (aapt2 dump badging base.apk -> versionCode 384510792).
                    // An earlier value here was a transposed digit, which made
                    // Morphe Manager call the bundle incompatible with the very
                    // APK it was built for.
                    AppTarget(
                        version = "439.0.0.37.89",
                        versionCodes =
                            mapOf(
                                SupportedAbi.ARM64_V8A to 384510792,
                            ),
                    ),
                    // 435 was listed for an old build. It is gone on purpose:
                    // a target that no current APKM matches only widens the
                    // surface for a manager to pick the wrong mapping.
                ),
        )

    /** Extension DEX merged by each patch below. Must match extensions/extension/build.gradle.kts. */
    const val EXTENSION_MPE = "extensions/extension.mpe"

    /** Own runtime package — deliberately NOT app.morphe.extension.instagram.* so the
     *  Piko bundle and this bundle never overwrite each other's dex classes. */
    const val EXT_CUSTOM = "Lapp/epitaph/extension/custom"
}
