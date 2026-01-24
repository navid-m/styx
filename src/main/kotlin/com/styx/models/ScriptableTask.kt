package com.styx.models

/**
 * Some scriptable task, simply a wrapper for ScriptableTaskDetails.
 * For maintainability purposes.
 */
data class ScriptableTask(
    val task: ScriptableTaskDetails
)