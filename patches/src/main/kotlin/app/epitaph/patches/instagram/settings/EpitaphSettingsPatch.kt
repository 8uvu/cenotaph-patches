package app.epitaph.patches.instagram.settings

import app.epitaph.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Registers EpitaphSettingsActivity + a launcher icon so the bundle has a
 * findable settings screen (the standalone has no Piko Settings by design).
 * Same manifest technique Piko uses for its SettingsActivity.
 */
val epitaphSettingsActivityPatch =
    resourcePatch(
        name = "Cenotaph - Settings app",
        description = "Adds the Cenotaph Settings launcher icon. It lists only the features this bundle actually hooks.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        finalize {
            document("AndroidManifest.xml").use { document ->

                val application = document.getElementsByTagName("application").item(0) as Element

                val activity = document.createElement("activity")
                activity.setAttribute("android:name", "app.epitaph.extension.custom.EpitaphSettingsActivity")
                activity.setAttribute("android:label", "Ceno")
                activity.setAttribute("android:theme", "@android:style/Theme.Material.NoActionBar")
                activity.setAttribute("android:exported", "true")

                val intentFilter = document.createElement("intent-filter")
                val action = document.createElement("action")
                action.setAttribute("android:name", "android.intent.action.MAIN")
                val category = document.createElement("category")
                category.setAttribute("android:name", "android.intent.category.LAUNCHER")
                intentFilter.appendChild(action)
                intentFilter.appendChild(category)
                activity.appendChild(intentFilter)

                application.appendChild(activity)
            }
        }
    }
