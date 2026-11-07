package app.epitaph.patches.instagram.themeplus

import app.epitaph.patches.instagram.runtime.runtimePatch
import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.hardAnchor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val HOOK_CLASS = "$EXT_CUSTOM/ThemePlusHook;"

/**
 * "+" entry inside Instagram's theme preview screen (CustomThemePreview-
 * Fragment.onViewCreated — verified by disassembly the only method app-wide
 * containing both themeToUse and themeInfo strings).
 *
 * Overlays a "+" button on the preview; tap opens Cenotaph Settings straight
 * into the image picker and saves the choice as that thread's wallpaper
 * override (thread resolved via Piko's tracker when fused alongside, else
 * the pick becomes the global wallpaper). p1 is the fragment root view, so
 * the call is signature-pinned with no register guessing.
 */
internal object ThemePreviewFingerprint : Fingerprint(
    name = "onViewCreated",
    returnType = "V",
    strings = listOf("themeToUse", "themeInfo"),
)

@Suppress("unused")
val themePlusPatch =
    bytecodePatch(
        name = "Cenotaph - Theme picker",
        description = "+ button on the chat theme preview: pick a wallpaper override for that thread (local only).",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)
        dependsOn(runtimePatch)

        execute {
            // Hard anchor: the "+" overlay on the theme preview screen is the
            // only way into the per-thread wallpaper override, so a miss must
            // be reported instead of producing an inert patch.
            hardAnchor("theme preview (onViewCreated: themeToUse + themeInfo)") {
                ThemePreviewFingerprint.method.apply {
                    val ret = instructions.first { it.opcode == Opcode.RETURN_VOID }
                    // `p1` is the fragment root View, but in this method it
                    // lives at v18 and a non-range invoke can only address
                    // v0..v15 (that is what failed the whole job with
                    // "Invalid register: v18"). The /range form takes any
                    // register, and using it for a single register avoids
                    // clobbering a live local with a move-object temp.
                    val p1 = implementation!!.registerCount - parameterTypes.size
                    addInstructions(
                        ret.location.index,
                        "invoke-static/range {v$p1 .. v$p1}, $HOOK_CLASS->addPlus(Landroid/view/View;)V",
                    )
                }
            }
        }
    }
