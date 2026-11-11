package app.epitaph.patches.instagram.privacy

import app.epitaph.patches.instagram.runtime.runtimePatch
import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.hardAnchor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string

private const val HOOK_CLASS = "$EXT_CUSTOM/EpitaphRuntime;"

/**
 * Instagram's feed/reel ad-pod predicate.
 *
 * Evidence (452.1.0.53.64, smali classes2/X/00zO.1.smali):
 *   public static final A02(LX/00zO;LX/012A;LX/04fw;)Z
 *   contains the log string "Is ad pod". Returning true from this predicate
 *   removes the pod, so the hook returns false before the original code runs.
 *
 * This is the only ad anchor included in v1. Piko's feed-insertion and
 * contextual-filter hooks depend on its hook-flags and menu code, which Cenotaph
 * does not carry yet.
 */
internal object DisableAdsFingerprint : Fingerprint(
    strings = listOf("Is ad pod"),
)

@Suppress("unused")
val disableAdsPatch =
    bytecodePatch(
        name = "Cenotaph - Disable ads",
        description = "Hides sponsored ad pods in the feed.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)
        dependsOn(runtimePatch)

        execute {
            hardAnchor("feed ad-pod predicate (\"Is ad pod\")") {
                DisableAdsFingerprint.method.apply {
                    addInstructions(
                        0,
                        """
                        invoke-static {}, $HOOK_CLASS->disableAds()Z
                        move-result v0
                        return v0
                        """.trimIndent(),
                    )
                }
            }
        }
    }
