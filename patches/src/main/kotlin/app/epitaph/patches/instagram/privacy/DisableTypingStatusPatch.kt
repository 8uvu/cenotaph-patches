package app.epitaph.patches.instagram.privacy

import app.epitaph.patches.instagram.runtime.runtimePatch
import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.hardAnchor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val HOOK_CLASS = "$EXT_CUSTOM/EpitaphRuntime;"

/**
 * Instagram's DM text-change handler, where the typing indicator is sent.
 *
 * Evidence (452.1.0.53.64, smali classes4/X/02Vi.1.smali):
 *   afterTextChanged(Editable) carries the literals 0x800033 and 0x800013 (the
 *   EditText input flags). onTextChanged(CharSequence,III)V is the same class and
 *   reads a boolean field before an IF_NEZ, which gates the typing-state send.
 *
 * The injected check returns early when the user has turned the option on, so
 * the typing state is never sent. The match is by literals and class shape, not
 * by obfuscated names.
 */
internal object DMAfterTextChangedFingerprint : Fingerprint(
    name = "afterTextChanged",
    filters = listOf(
        literal(8388659L), // 0x800033
        literal(8388627L), // 0x800013
    ),
)

internal object DMOnTextChangedFingerprint : Fingerprint(
    name = "onTextChanged",
    classFingerprint = DMAfterTextChangedFingerprint,
)

@Suppress("unused")
val disableTypingStatusPatch =
    bytecodePatch(
        name = "Cenotaph - Disable typing status",
        description = "Stops Cenotaph from telling a chat that you are typing.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)
        dependsOn(runtimePatch)

        execute {
            hardAnchor("DM onTextChanged typing-state gate (0x800033 / 0x800013)") {
                DMOnTextChangedFingerprint.method.apply {
                    val gate = instructions.firstOrNull {
                        if (it.opcode != Opcode.IGET_BOOLEAN) return@firstOrNull false
                        getInstruction(it.location.index + 1).opcode == Opcode.IF_NEZ
                    } ?: throw IllegalStateException("typing gate not found")

                    val index = gate.location.index
                    val next = getInstruction(index + 1)
                    val register = (next as OneRegisterInstruction).registerA
                    addInstructionsWithLabels(
                        index,
                        """
                        invoke-static {}, $HOOK_CLASS->disableTypingStatus()Z
                        move-result v$register
                        if-eqz v$register, :cenotaph_original
                        return-void
                        """.trimIndent(),
                        ExternalLabel("cenotaph_original", next),
                    )
                }
            }
        }
    }
