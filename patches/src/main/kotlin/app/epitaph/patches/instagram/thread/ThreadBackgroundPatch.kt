package app.epitaph.patches.instagram.thread

import app.epitaph.patches.instagram.runtime.runtimePatch
import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.epitaph.patches.shared.Constants.EXTENSION_MPE
import app.epitaph.patches.shared.Constants.EXT_CUSTOM
import app.epitaph.patches.shared.hardAnchor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch

private const val HOOK_CLASS = "$EXT_CUSTOM/EpitaphRuntime;"

/**
 * Instagram's own thread-background setter.
 *
 * Evidence (447.0.0.55.81, smali_classes3/X/6p0.smali):
 *
 *     .method public final G8p(Ljava/lang/String;Landroid/graphics/Bitmap;)V
 *         const v0, 0x7f0b2011                       # id/immersive_thread_theme_background_stub
 *         invoke-virtual {v1, v0}, Landroid/view/View;->requireViewById(I)Landroid/view/View;
 *         check-cast v0, Landroid/view/ViewStub;
 *         invoke-virtual {v0}, Landroid/view/ViewStub;->inflate()Landroid/view/View;
 *         const v0, 0x7f0b200f                       # id/immersive_thread_background_image_view
 *         check-cast v1, Landroid/widget/ImageView;
 *         invoke-virtual {v1, v4}, Landroid/widget/ImageView;->setImageBitmap(Landroid/graphics/Bitmap;)V
 *
 * This is the app painting a background image behind a chat, which is exactly
 * what the wallpaper feature wants to control. Intercepting the Bitmap argument
 * means the image lands in Instagram's own ImageView, at the moment the app
 * sets it — no view-tree scanning, no guessing which activity is open.
 *
 * Matched without the obfuscated method name: a void method taking
 * (String, Bitmap) that sets an ImageView's bitmap. That shape is the app's
 * background setter on both tested builds, and the resource ids are deliberately
 * not part of the fingerprint because they change every release.
 */
internal object ThreadBackgroundFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Landroid/graphics/Bitmap;"),
    filters = listOf(
        methodCall(name = "setImageBitmap", parameters = listOf("Landroid/graphics/Bitmap;")),
    ),
)

/**
 * Replaces the bitmap Instagram is about to draw when the user has configured a
 * wallpaper, and returns it untouched otherwise.
 *
 * `p2` is the Bitmap parameter. It sits above v15, so the invoke has to be the
 * `/range` form; the result is written straight back into the parameter, which
 * keeps every later use of that bitmap consistent.
 */
@Suppress("unused")
val threadBackgroundPatch =
    bytecodePatch(
        name = "Cenotaph - Chat wallpaper hook",
        description = "Feeds Cenotaph's wallpaper into Instagram's own thread background image, where the app sets it.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        extendWith(EXTENSION_MPE)
        dependsOn(runtimePatch)

        execute {
            hardAnchor("thread background setter (String, Bitmap) -> ImageView.setImageBitmap") {
                ThreadBackgroundFingerprint.method.apply {
                    addInstructions(
                        0,
                        """
                        invoke-static/range {p2 .. p2}, $HOOK_CLASS->themeWallpaper(Landroid/graphics/Bitmap;)Landroid/graphics/Bitmap;
                        move-result-object p2
                        """.trimIndent(),
                    )
                }
            }
        }
    }
