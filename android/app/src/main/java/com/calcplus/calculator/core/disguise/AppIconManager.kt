package com.calcplus.calculator.core.disguise

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The `activity-alias` names declared in the manifest, one per cover identity
 * (§9a). Kept together so the strings the faces hand [AppIconManager] and the
 * strings the manifest declares can be checked against each other at a glance
 * — a typo here silently produces an app with no launcher entry.
 */
object CoverAliases {
    private const val APP = "com.calcplus.calculator"

    const val CALCULATOR = "$APP.CalculatorAlias"
    const val NOTEPAD = "$APP.NotepadAlias"
    const val GALLERY = "$APP.GalleryAlias"
}

/**
 * Cover identities on the home screen (iteration-3-decisions §9a).
 *
 * Each lock face has one `activity-alias` in the manifest, carrying its own
 * icon and label and a LAUNCHER intent filter, all targeting `MainActivity`.
 * The activity itself is not a launcher entry. Switching the enrolled face
 * swaps which alias is enabled, and the launcher picks up the new icon and
 * name.
 *
 * Two safety rules, both mandatory:
 *
 * 1. **Enable before disable.** The incoming alias is enabled first, and only
 *    then are the others disabled. Done the other way round there is a window
 *    — however brief, and unbounded if the process dies in it — where NO
 *    launcher entry exists and the app has simply vanished from the home
 *    screen with a vault inside it.
 * 2. **`DONT_KILL_APP`.** Without it the framework kills the process to apply
 *    the change. The swap happens immediately after a successful envelope
 *    write, with the user looking at the app; being killed there would read as
 *    a crash and, on a switch, would drop the confirmation.
 *
 * Everything here is best-effort and silent. A failure is swallowed: the vault
 * is already re-enrolled by the time this runs, so a stale icon is cosmetic and
 * must never block or undo the switch. Nothing is logged — not the face, not
 * the alias, not the outcome. A log line naming "GalleryAlias" would tell a
 * reader of `logcat` exactly which disguise this install wears.
 */
class AppIconManager(
    /** Every cover alias, in registry order; the first is the manifest default. */
    private val aliases: List<String>,
    private val switcher: ComponentSwitcher,
) {
    init {
        require(aliases.isNotEmpty()) { "at least the default cover alias is required" }
        require(aliases.toSet().size == aliases.size) { "cover aliases must be unique" }
    }

    /** Wear [face]'s cover identity. Unknown faces are ignored. */
    fun apply(face: DisguiseProvider) = apply(face.coverAlias)

    /**
     * Wear the identity of [alias]. No-op when it is already the only enabled
     * one; an alias this manager does not know is ignored rather than left to
     * disable every entry it does know.
     */
    fun apply(alias: String) {
        if (alias !in aliases) return
        runCatching {
            val others = aliases.filter { it != alias }
            if (switcher.isEnabled(alias) && others.none { switcher.isEnabled(it) }) return
            // Rule 1: the incoming entry exists before any outgoing one stops.
            switcher.setEnabled(alias, true)
            others.forEach { switcher.setEnabled(it, false) }
        }
    }

    /** The seam over [PackageManager], so the ordering above is testable. */
    interface ComponentSwitcher {
        fun isEnabled(alias: String): Boolean
        fun setEnabled(alias: String, enabled: Boolean)
    }

    companion object {
        /**
         * The real thing. [defaultAlias] is the one the manifest ships enabled,
         * so `COMPONENT_ENABLED_STATE_DEFAULT` can be answered without asking
         * the package manager to parse the manifest for us.
         */
        fun create(context: Context, aliases: List<String>): AppIconManager {
            val appContext = context.applicationContext
            return AppIconManager(
                aliases = aliases,
                switcher = PackageManagerSwitcher(
                    packageManager = appContext.packageManager,
                    packageName = appContext.packageName,
                    defaultAlias = aliases.first(),
                ),
            )
        }
    }

    private class PackageManagerSwitcher(
        private val packageManager: PackageManager,
        private val packageName: String,
        private val defaultAlias: String,
    ) : ComponentSwitcher {
        override fun isEnabled(alias: String): Boolean =
            when (packageManager.getComponentEnabledSetting(component(alias))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
                -> false
                // DEFAULT means "whatever the manifest says", and the manifest
                // ships exactly one alias enabled.
                else -> alias == defaultAlias
            }

        override fun setEnabled(alias: String, enabled: Boolean) {
            packageManager.setComponentEnabledSetting(
                component(alias),
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                // Rule 2: never kill the process out from under the user.
                PackageManager.DONT_KILL_APP,
            )
        }

        private fun component(alias: String) = ComponentName(packageName, alias)
    }
}
