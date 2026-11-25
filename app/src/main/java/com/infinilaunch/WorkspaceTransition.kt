package com.infinilaunch

internal data class WorkspaceChange(val from: String, val to: String, val direction: Int)

/** The host owns gesture and view lifetimes. A future transition can commit the prepared change. */
internal fun interface WorkspaceTransition {
    fun switch(change: WorkspaceChange, commit: () -> Unit)

    companion object {
        val Immediate = WorkspaceTransition { _, commit -> commit() }
    }
}
