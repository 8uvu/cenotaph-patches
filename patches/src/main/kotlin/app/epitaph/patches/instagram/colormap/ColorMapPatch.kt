package app.epitaph.patches.instagram.colormap

import app.epitaph.patches.instagram.runtime.runtimePatch
import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.injectApplicationCall
import app.morphe.patcher.patch.bytecodePatch

/**
 * Local colour map: "#RRGGBB=>#RRGGBB" pairs from Cenotaph Settings, applied to
 * the resumed screen's view tree. Exact RGB match, alpha preserved, unreadable
 * colours left alone.
 */
@Suppress("unused")
val colorMapPatch =
    bytecodePatch(
        name = "Cenotaph - Colour map",
        description = "Remaps UI colours to a local palette from hex pairs in Cenotaph Settings. Local only.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)
        dependsOn(runtimePatch)

        execute {
            injectApplicationCall("$EXT_CUSTOM/EpitaphRuntime;->enableColorMap()V")
        }
    }
