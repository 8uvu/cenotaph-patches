package app.epitaph.patches.shared

import app.morphe.patcher.patch.PatchException

/**
 * Anchor policies.
 *
 * The bundle used to wrap every injection in `runCatching { }`, which turned a
 * moved anchor into a feature that does nothing and reports success. That is
 * how "Custom chat wallpaper" and "DM streaks" stayed broken without a single
 * message. Every injection now states which of the two cases it is:
 *
 *  - [hardAnchor] — this anchor is the only way the feature can ever run, so a
 *    miss is an error the user must see. Morphe Manager then reports the patch
 *    as failed instead of installing something inert.
 *  - [softAnchor] — the feature has a second driver (the `Epitaph runtime`
 *    patch), so a miss only costs the extra trigger. Documented, deliberate,
 *    never the only mechanism.
 */
internal inline fun hardAnchor(what: String, block: () -> Unit) {
    try {
        block()
    } catch (e: PatchException) {
        throw e
    } catch (e: Throwable) {
        throw PatchException(
            "Cenotaph: could not resolve the $what anchor on this Instagram build. " +
                "The feature would do nothing, so the patch stops here instead of " +
                "installing silently broken.",
            e,
        )
    }
}

internal inline fun softAnchor(block: () -> Unit) {
    try {
        block()
    } catch (ignored: Throwable) {
        // Degrades by design: the runtime dispatch still drives this feature.
    }
}
