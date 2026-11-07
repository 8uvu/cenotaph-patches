package app.epitaph.patches.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method

private const val APPLICATION = "Landroid/app/Application;"

/**
 * In order of preference. onCreate is the normal entry; attachBaseContext is the
 * fallback for a build whose Application does not declare onCreate.
 */
private val ENTRY_POINTS =
    listOf(
        listOf("onCreate", "V"),
        listOf("attachBaseContext", "Landroid/content/Context;", "V"),
    )

private fun entryPoint(classDef: ClassDef): Method? =
    ENTRY_POINTS.asSequence()
        .mapNotNull { spec ->
            val name = spec[0]
            val returnType = spec.last()
            val parameters = spec.subList(1, spec.size - 1)
            classDef.methods.firstOrNull { m ->
                m.name == name &&
                    m.returnType == returnType &&
                    m.parameterTypes.map { it.toString() } == parameters
            }
        }
        .firstOrNull()

/**
 * Instagram's Application class, found by walking the dex hierarchy.
 *
 * Obfuscation renames classes but never framework superclasses, so "whichever
 * class extends android.app.Application" keeps working across releases, and —
 * unlike a string fingerprint — it cannot quietly stop matching.
 */
internal fun BytecodePatchContext.applicationClass(): ClassDef {
    val supers = HashMap<String, String?>()
    classDefForEach { cd -> supers[cd.type] = cd.superclass }

    fun extendsApplication(type: String): Boolean {
        var t: String? = type
        var hops = 0
        while (t != null && hops < 16) {
            if (t == APPLICATION) return true
            t = supers[t]
            hops++
        }
        return false
    }

    val direct = supers.filterValues { it == APPLICATION }.keys.toList()
    val candidates =
        direct.ifEmpty { supers.keys.filter { it != APPLICATION && extendsApplication(it) } }

    for (type in candidates) {
        val cd = classDefByOrNull(type) ?: continue
        if (entryPoint(cd) != null) return cd
    }

    // Last resort: a shell class by name, for a build whose hierarchy is
    // wrapped one level deeper than the walk reaches.
    for (type in supers.keys) {
        if (!type.endsWith("/InstagramAppShell;")) continue
        val cd = classDefByOrNull(type) ?: continue
        if (entryPoint(cd) != null) return cd
    }

    throw PatchException(
        "Cenotaph: no class in this APK extends android.app.Application and declares " +
            "onCreate()V / attachBaseContext(Context)V. Either the APK is not an " +
            "application or it is incomplete, so nothing can be hooked.",
    )
}

/**
 * Injects one `invoke-static {}` call into Instagram's Application entry method.
 *
 * The call must take no arguments. That is not a style choice: `invoke-static
 * {p0}` is a non-range invoke and p0 lives in v19 in Instagram's Application
 * class, outside the v0..v15 a non-range invoke can address. The patcher then
 * aborts the whole job with "Invalid register: v19", which looks to the user
 * like every patch in the bundle being broken. An empty register list is valid
 * in every method shape.
 *
 * Also awaits the injected call landing: a patch that reports "Applied" while
 * writing nothing is worse than one that refuses.
 */
internal fun BytecodePatchContext.injectApplicationCall(method: String) {
    val classDef = applicationClass()
    val reference =
        entryPoint(classDef)
            ?: throw PatchException(
                "Cenotaph: ${classDef.type} declares no onCreate()V or " +
                    "attachBaseContext(Context)V, so there is nowhere to hook.",
            )

    val target =
        mutableClassDefBy(classDef.type)
            .methods
            .firstOrNull { m ->
                m.name == reference.name &&
                    m.parameterTypes.map { it.toString() } ==
                    reference.parameterTypes.map { p -> p.toString() }
            }
            ?: throw PatchException(
                "Cenotaph: could not reopen ${classDef.type}->${reference.name} for writing.",
            )

    // Insert after the super call when there is one: Instagram's own setup runs
    // first and cannot undo the registration. Index 0 for a method with none.
    val instructions = target.implementation?.instructions?.toList().orEmpty()
    val superIndex =
        instructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_SUPER || it.opcode == Opcode.INVOKE_SUPER_RANGE
        }
    val before = target.implementation?.instructions?.count() ?: 0
    target.addInstructions(if (superIndex >= 0) superIndex + 1 else 0, "invoke-static {}, $method")

    val after = target.implementation?.instructions?.count() ?: 0
    check(after > before) {
        "Cenotaph: injection of $method into ${classDef.type}->${reference.name} did not " +
            "land (instructions $before -> $after)."
    }
}
