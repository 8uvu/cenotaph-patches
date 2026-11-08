package app.epitaph.patches.instagram.bubbleshape

import app.epitaph.patches.instagram.runtime.runtimePatch
import app.epitaph.patches.instagram.thread.threadSurfacePatch
import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.injectApplicationCall
import app.morphe.patcher.patch.bytecodePatch

/**
 * Corner radius for surfaces that are already rounded: bubbles, the composer,
 * rounded buttons. A sharp rectangle is never rounded, and multi-corner
 * drawables are skipped.
 */
@Suppress("unused")
val bubbleShapePatch =
    bytecodePatch(
        name = "Cenotaph - Bubble corners",
        description = "Sets the corner radius of rounded chat surfaces from Cenotaph Settings (0 = square).",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)
        dependsOn(runtimePatch)
        dependsOn(threadSurfacePatch)

        execute {
            injectApplicationCall("$EXT_CUSTOM/EpitaphRuntime;->enableBubbles()V")
        }
    }
