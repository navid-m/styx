package com.styx.models

data class ScriptableTaskDetails(
    val name: String,
    val arch: String? = null,
    val prefix: String? = null,
    val description: String? = null,
    val app: String? = null,
    val executable: String? = null,
    val exclude_processes: String? = null
)