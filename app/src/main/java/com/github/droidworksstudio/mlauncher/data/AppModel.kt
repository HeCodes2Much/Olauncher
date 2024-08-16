package com.github.droidworksstudio.mlauncher.data

import android.os.UserHandle
import java.text.Collator

val collator: Collator = Collator.getInstance()

// TODO rename the class ? AppListItem
// TODO: rename fields: cut off the `app` prefix
// TODO make difference between data from the system, and from the prefs (extract object?)
/**
 * @property priority user-defined value which affects ordering.
 * Items with higher priority appear higher in the list.
 * Default priority is 0.
 * You can playfully fine-tune the order by adding a fractional part to priority: 6.5, 10.125.
 *
 * TODO why the instances are created in 3 different places?
 */
data class AppModel(
    val appLabel: String,
    val appPackage: String,
    val appActivityName: String,
    val user: UserHandle, // TODO doc why it matters
    var appAlias: String, // TODO make immutable by refining data flow
    val priority: Double,
) : Comparable<AppModel> {
    // TODO rename. Visible name?
    val name = appLabel.ifEmpty { appAlias }

    /** Speed up the sort and search */
    private val collationKey = collator.getCollationKey(name)

    /**
     * Compare by `priority`, then by name.
     * It works like `ORDER BY rank, priority` in sql
     */
    override fun compareTo(other: AppModel): Int {
        priority.compareTo(other.priority).also { if (it != 0) return it}
        return collationKey.compareTo(other.collationKey)
    }
}
