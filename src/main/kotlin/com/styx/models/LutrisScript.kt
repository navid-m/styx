package com.styx.models

data class LutrisScript(
    val files: List<LutrisFile>? = null,
    val game: LutrisGame? = null,
    val installer: List<LutrisTask>? = null,
    val system: LutrisSystem? = null,
    val wine: LutrisWine? = null
)

data class LutrisFile(
    val id: String,
    val url: String
)

data class LutrisGame(
    val arch: String? = null,
    val exe: String? = null,
    val prefix: String? = null
)

data class LutrisTask(
    val task: LutrisTaskDetails
)

data class LutrisTaskDetails(
    val name: String,
    val arch: String? = null,
    val prefix: String? = null,
    val description: String? = null,
    val app: String? = null,
    val executable: String? = null,
    val exclude_processes: String? = null
)

data class LutrisSystem(
    val env: Map<String, String>? = null
)

data class LutrisWine(
    val overrides: Map<String, String>? = null
)
