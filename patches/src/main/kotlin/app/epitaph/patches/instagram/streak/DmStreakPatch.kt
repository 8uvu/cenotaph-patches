package app.epitaph.patches.instagram.streak

import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.hardAnchor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.booleanOption

private const val HOOK_CLASS = "$EXT_CUSTOM/StreakHook;"

/**
 * Client-only DM streak: counts a day for each DM thread opened on this device.
 *
 * Anchor evidence (452.1.0.53.64, smali classes4/X/0Bz1.smali `A0P(View)V`):
 * the thread root view is resolved with requireViewById and cast to
 * CustomFadingEdgeRecyclerView. Same anchor as the thread surface patch.
 *
 * The hook passes the root view and a placeholder thread key. Per-thread keys
 * are not wired yet, so the streak is per screen, not per chat.
 */
internal object DmStreakFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        methodCall(name = "requireViewById", returnType = "Landroid/view/View;"),
        checkCast(type = "Lcom/instagram/ui/recyclerview/CustomFadingEdgeRecyclerView;"),
    ),
)

@Suppress("unused")
val dmStreakPatch =
    bytecodePatch(
        name = "Cenotaph - DM streak (client only)",
        description = "Shows a client-side day streak on DM threads. Nothing is sent to anyone.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)

        val showStreak by booleanOption(
            key = "showStreak",
            default = true,
            title = "Show DM streak",
            description = "Count days you open a DM thread on this device. Nothing is sent.",
        )

        execute {
            if (showStreak == true) hardAnchor("DM thread root view (streak open-day counter)") {
                DmStreakFingerprint.method.apply {
                    addInstructions(
                        0,
                        "invoke-static/range {p1 .. p1}, $HOOK_CLASS->onThreadOpened(Landroid/view/View;)V",
                    )
                }
            }
        }
    }
