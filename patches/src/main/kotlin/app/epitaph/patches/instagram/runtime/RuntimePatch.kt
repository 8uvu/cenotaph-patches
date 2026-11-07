package app.epitaph.patches.instagram.runtime

import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.injectApplicationCall
import app.morphe.patcher.patch.bytecodePatch

private const val HOOK_CLASS = "$EXT_CUSTOM/EpitaphRuntime;"

/**
 * The hook every feature rides on: one call into Instagram's own Application.
 *
 * Feature patches each inject their own registration call next to this one, so
 * a feature the user leaves unticked is genuinely not enabled — the code is in
 * the merged extension either way, but nothing turns its hook on.
 *
 * Diagnostics: adb logcat -s CenotaphRuntime
 */
@Suppress("unused")
val runtimePatch =
    bytecodePatch(
        name = "Cenotaph - Runtime (required)",
        description = "Lifecycle hook into Instagram's Application. Every other Cenotaph patch registers here; leave it enabled.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)

        execute {
            injectApplicationCall("$HOOK_CLASS->install()V")
        }
    }
