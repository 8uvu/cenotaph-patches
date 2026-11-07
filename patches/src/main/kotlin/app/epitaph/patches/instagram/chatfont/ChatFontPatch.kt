package app.epitaph.patches.instagram.chatfont

import app.epitaph.patches.instagram.runtime.runtimePatch
import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.injectApplicationCall
import app.morphe.patcher.patch.bytecodePatch

/**
 * Local font file applied to chat text. A missing or unreadable file is a no-op;
 * bold text keeps a derived bold face rather than being flattened.
 */
@Suppress("unused")
val chatFontPatch =
    bytecodePatch(
        name = "Cenotaph - Local chat font",
        description = "Applies a local .ttf/.otf to chat text from Cenotaph Settings. Local only.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)
        dependsOn(runtimePatch)

        execute {
            injectApplicationCall("$EXT_CUSTOM/EpitaphRuntime;->enableFont()V")
        }
    }
