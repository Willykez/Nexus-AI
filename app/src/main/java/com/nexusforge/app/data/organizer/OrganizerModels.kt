package com.nexusforge.app.data.organizer

import kotlinx.serialization.Serializable

/** One file the AI extracted from a pasted source dump. Path is relative, forward-slash separated. */
@Serializable
data class ParsedFile(val path: String, val content: String)

/**
 * The model is instructed to respond with exactly this shape. [projectName] is kept separate
 * from each file's path so the writer can nest everything under one deterministic root folder
 * instead of hoping the model prefixed every path consistently.
 */
@Serializable
data class ParsedProject(val projectName: String = "", val files: List<ParsedFile> = emptyList())

data class LogLine(val text: String, val isError: Boolean = false)
