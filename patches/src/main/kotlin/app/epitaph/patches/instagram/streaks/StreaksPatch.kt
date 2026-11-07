package app.epitaph.patches.instagram.streaks

import app.epitaph.patches.instagram.runtime.runtimePatch
import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.injectApplicationCall
import app.morphe.patcher.patch.bytecodePatch

/**
 * Counts consecutive days each conversation is opened and draws the count on the
 * thread header, each DM-list row, and as a summary next to Messages.
 *
 * Local bookkeeping only — nothing is read from or written to Instagram.
 */
@Suppress("unused")
val streaksPatch =
    bytecodePatch(
        name = "Cenotaph - DM streaks",
        description = "Fire badge per conversation, on the thread header, plus a summary next to Messages. Counted locally.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)
        dependsOn(runtimePatch)

        execute {
            injectApplicationCall("$EXT_CUSTOM/EpitaphRuntime;->enableStreaks()V")
        }
    }
