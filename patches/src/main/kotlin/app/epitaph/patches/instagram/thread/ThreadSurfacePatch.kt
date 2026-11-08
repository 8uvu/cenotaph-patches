package app.epitaph.patches.instagram.thread

import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.hardAnchor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch

private const val HOOK_CLASS = "$EXT_CUSTOM/EpitaphRuntime;"

/**
 * The DM thread's root-view handler.
 *
 * Evidence (447.0.0.55.81, smali_classes3/X/6q2.smali):
 *
 *     .method public A0Z(Landroid/view/View;)V
 *         const v0, 0x7f0b26b4                      # id/message_list
 *         invoke-virtual {v3, v0}, Landroid/view/View;->requireViewById(I)Landroid/view/View;
 *         move-result-object v2
 *         check-cast v2, Lcom/instagram/ui/recyclerview/CustomFadingEdgeRecyclerView;
 *
 * This is the first place the thread screen has its own view tree in hand: the
 * method is handed the fragment root and immediately resolves the message list
 * out of it. That makes it the correct place to hand the real screen to the
 * hooks.
 *
 * The anchor deliberately does NOT match on the `message_list` resource id — a
 * resource id changes with every build and would pin the bundle to one APK. It
 * matches the two things that do not: a method taking exactly `android.view.View`
 * and returning void, which casts a `requireViewById` result to Instagram's own
 * (unobfuscated) `CustomFadingEdgeRecyclerView`. The `CustomFadingEdgeRecyclerView`
 * type is app code that obfuscation never renames, so the match survives a
 * recompile of Instagram.
 */
internal object ThreadRootFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        methodCall(name = "requireViewById", returnType = "Landroid/view/View;"),
        checkCast(type = "Lcom/instagram/ui/recyclerview/CustomFadingEdgeRecyclerView;"),
    ),
)

/**
 * Hands the DM thread's root view to the extension.
 *
 * Before this existed every UI hook ran on whichever activity happened to be
 * resumed and then *guessed* whether it was a thread screen by walking the view
 * tree for a resource name. That is why a feature could report success and do
 * nothing: the hook ran, the screen was not the one it expected, and the code
 * silently returned.
 *
 * Now the run happens exactly where Instagram itself resolves the thread's
 * message list, with the root view as an argument. No scanning, no guessing,
 * and a miss is reported instead of installing an inert patch.
 *
 * `p1` is the `View` parameter. It lives above v15, so the invoke must be the
 * `/range` form — a non-range `invoke-static {p1}` fails to assemble.
 */
@Suppress("unused")
val threadSurfacePatch =
    bytecodePatch(
        name = "Cenotaph - Thread anchor (required)",
        description = "Hooks the DM thread screen and hands its root view to the other Cenotaph patches. Leave it enabled.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)

        execute {
            hardAnchor("DM thread root view (requireViewById + CustomFadingEdgeRecyclerView)") {
                ThreadRootFingerprint.method.apply {
                    addInstructions(
                        0,
                        "invoke-static/range {p1 .. p1}, $HOOK_CLASS->attachThreadView(Landroid/view/View;)V",
                    )
                }
            }
        }
    }
