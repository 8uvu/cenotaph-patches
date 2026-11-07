package app.epitaph.patches.instagram.prows

import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val HOOK_CLASS = "$EXT_CUSTOM/EpitaphPikoRows;"

/**
 * Piko's settings home builder. Piko's settings screen is unobfuscated
 * extension code, so this pins the home-page builder by exact method name plus
 * a home-only string. Piko's home page is a list of one-row categories, and
 * this is where the single "Epitaph" row is appended.
 */
internal object PikoHomeFingerprint : Fingerprint(
    name = "buildSettingsPage",
    returnType = "V",
    strings = listOf("piko_category_about"),
)

/**
 * Piko's settings fragment: reads `piko_frag_name` from its arguments and calls
 * one `ScreenBuilder` method per category. There is no branch for a name Piko
 * does not know, so without this anchor our screen would open empty.
 *
 * Anchored by exact class and method name rather than by string, which is safe
 * here and only here: Piko's extension module is built with `-dontobfuscate`
 * and `-keep class app.morphe.**`, so unlike Instagram's own classes these names
 * do not change between releases. The parameter and return type keep it pinned
 * to the one `onCreate(Bundle)`.
 */
internal object PikoFragmentFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/instagram/settings/SettingsActivity\$SettingsFragment;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

/**
 * Own "Epitaph" category in Piko Settings, built the way Piko builds its own:
 * one home row that opens a dedicated screen.
 *
 * All Piko access is reflection inside the hook (zero Piko imports here), and
 * any Piko refactor means the category simply does not appear.
 */
@Suppress("unused")
val epitaphPikoRowsPatch =
    bytecodePatch(
        name = "Cenotaph - Category in Piko Settings",
        description = "Adds a Cenotaph category screen to Piko Settings (wallpaper, tint, bubbles, font, privacy). No-op unless Piko is fused alongside.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)

        execute {
            // Order matters. The row must only be published once we can fill the
            // screen it opens: Piko has no builder for our fragment name, so an
            // anchor miss here would leave the user on an empty screen. Populate
            // first, then publish; if the fragment cannot be anchored, the
            // category never appears and Piko's own settings stay untouched.
            val populated = runCatching {
                PikoFragmentFingerprint.method.apply {
                    val ret = instructions.first { it.opcode == Opcode.RETURN_VOID }
                    addInstructions(
                        ret.location.index,
                        "invoke-static {p0}, $HOOK_CLASS->injectFragment(Ljava/lang/Object;)V",
                    )
                }
            }.isSuccess

            if (populated) {
                runCatching {
                    PikoHomeFingerprint.method.apply {
                        val ret = instructions.first { it.opcode == Opcode.RETURN_VOID }
                        addInstructions(
                            ret.location.index,
                            "invoke-static {p0}, $HOOK_CLASS->injectHome(Ljava/lang/Object;)V",
                        )
                    }
                }
            }
        }
    }
