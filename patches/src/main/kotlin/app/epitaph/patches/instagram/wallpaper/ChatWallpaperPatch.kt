package app.epitaph.patches.instagram.wallpaper

import app.epitaph.patches.instagram.runtime.runtimePatch
import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.injectApplicationCall
import app.morphe.patcher.patch.bytecodePatch

/**
 * Chat wallpaper, chat tint and the "Seen by" line.
 *
 * All three paint on the thread screen when it resumes; one registration turns
 * them on together because they share the same surface and the same settings
 * section.
 */
@Suppress("unused")
val chatWallpaperPatch =
    bytecodePatch(
        name = "Cenotaph - Chat wallpaper",
        description = "Background image behind DM threads, plus chat tint and hiding the Seen-by line. Configure in Cenotaph Settings.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)
        dependsOn(runtimePatch)

        execute {
            injectApplicationCall("$EXT_CUSTOM/EpitaphRuntime;->enableWallpaper()V")
        }
    }
